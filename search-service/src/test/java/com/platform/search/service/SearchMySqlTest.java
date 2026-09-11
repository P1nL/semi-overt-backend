package com.platform.search.service;

import com.platform.search.mapper.*;
import com.platform.search.util.SearchKeywordNormalizer;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes production MyBatis providers on real MySQL, including optional ngram FULLTEXT. */
@EnabledIfEnvironmentVariable(named="S4_MYSQL_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:13306/")
class SearchMySqlTest {
    Connection connection;
    SqlSession session;
    SearchArticleMapper articles;
    SearchUserMapper users;

    @BeforeEach void setup() throws Exception {
        String base=System.getenv("S4_MYSQL_URL"), password=System.getenv("S4_MYSQL_PASSWORD");
        String schema="s4_search_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(base,"root",password);var s=c.createStatement()) {
            s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String url=base+schema+"?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        connection=DriverManager.getConnection(url,"root",password);
        sql("CREATE TABLE articles(id BIGINT PRIMARY KEY,author_id BIGINT,title VARCHAR(120),summary VARCHAR(255),content LONGTEXT,cover_url VARCHAR(512),cover_color VARCHAR(32),word_count INT,read_minutes DECIMAL(6,1),duration_category VARCHAR(20),status VARCHAR(20),deleted BOOLEAN,published_at DATETIME,updated_at DATETIME,created_at DATETIME)");
        sql("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(32),nickname VARCHAR(60),avatar_url VARCHAR(255))");
        sql("INSERT INTO users VALUES(1,'nebula_writer','星云作者',NULL),(2,'other','Other',NULL)");
        article(1,"nebula","","<p>normal</p>","APPROVED",false);
        article(2,"nebula guide","","<p>normal</p>","APPROVED",false);
        article(3,"body match","","<p>nebula 正文</p>","APPROVED",false);
        article(4,"hidden","","<script>phantom</script><style>.ghost{}</style><p>safe</p>","APPROVED",false);
        article(5,"links","","[Visible anchor](https://urlphantom.invalid)","APPROVED",false);
        article(6,"secretphantom","","nebula","DRAFT",false);
        article(7,"secretphantom","","nebula","APPROVED",true);
        var cfg=new Configuration(new Environment("s4",new JdbcTransactionFactory(),new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,"root",password)));
        // Each production SqlSessionTemplate call outside a transaction gets a
        // fresh session; this long-lived test session must not cache pre-DDL results.
        cfg.setLocalCacheScope(LocalCacheScope.STATEMENT);
        cfg.setMapUnderscoreToCamelCase(true);cfg.addMapper(SearchArticleMapper.class);cfg.addMapper(SearchUserMapper.class);
        session=new SqlSessionFactoryBuilder().build(cfg).openSession(true);
        articles=session.getMapper(SearchArticleMapper.class);users=session.getMapper(SearchUserMapper.class);
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(connection!=null)connection.close();}
    void sql(String statement)throws Exception {try(var s=connection.createStatement()){s.execute(statement);}}
    void article(long id,String title,String summary,String body,String status,boolean deleted)throws Exception {
        try(var p=connection.prepareStatement("INSERT INTO articles VALUES(?,1,?,?,?,NULL,NULL,100,1,'QUICK',?,?,NOW(),NOW(),NOW())")) {
            p.setLong(1,id);p.setString(2,title);p.setString(3,summary);p.setString(4,body);p.setString(5,status);p.setBoolean(6,deleted);p.executeUpdate();
        }
    }
    List<Long> ids(String keyword,boolean fulltext) {
        return articles.searchByKeyword(SearchKeywordNormalizer.article(keyword),fulltext,0,50).stream().map(x->x.getArticleId()).toList();
    }
    @Test void realProvidersCleanRankPaginateAndKeepPrivateContentOut() {
        assertEquals(List.of(1L,2L,3L),ids("nebula",false));
        for(String hidden:List.of("phantom","ghost","urlphantom","secretphantom")) assertEquals(List.of(),ids(hidden,false));
        var q=SearchKeywordNormalizer.article("nebula");assertEquals(3,articles.countByKeyword(q,false));
        assertEquals(2L,articles.searchByKeyword(q,false,1,1).get(0).getArticleId());
        assertTrue(articles.searchByKeyword(q,false,Long.MAX_VALUE/2,1).isEmpty());
        assertEquals(1,users.countByKeyword(SearchKeywordNormalizer.user("nebula writer")));
        assertEquals(1L,users.searchByKeyword(SearchKeywordNormalizer.user("星云"),0,10).get(0).getId());
        assertTrue(ids("%",false).isEmpty());assertTrue(ids("' OR 1=1 --",false).isEmpty());
    }
    @Test void fulltextCapabilityAndFallbackRetainRecallAndCounts() throws Exception {
        var capability=new SearchIndexCapability(articles,true,"ft_articles_search");assertFalse(capability.inspect().usable());
        sql("CREATE FULLTEXT INDEX ft_articles_search ON articles(title,summary,content) WITH PARSER ngram");
        assertTrue(capability.inspect().usable());
        for(String keyword:List.of("nebula","neb","a","星云","phantom","secretphantom")) {
            var q=SearchKeywordNormalizer.article(keyword);
            assertEquals(articles.countByKeyword(q,false),articles.countByKeyword(q,true));
            assertEquals(new HashSet<>(ids(keyword,false)),new HashSet<>(ids(keyword,true)));
        }
        assertEquals(List.of(1L,2L,3L),ids("nebula",true));
        sql("ALTER TABLE articles DROP INDEX ft_articles_search");assertFalse(capability.inspect().usable());
        assertEquals(List.of(1L,2L,3L),ids("nebula",false));
    }
    @Test void laterVisibleHtmlAndMarkdownHeadingsOutrankBodyAndHiddenBlocksDoNot() throws Exception {
        article(20,"generic","","<h1>first</h1><p>intervening</p><h2>quasar</h2>","APPROVED",false);
        article(21,"generic","","# first\n\n## quasar\n\nbody","APPROVED",false);
        article(22,"generic","","<p>quasar body</p>","APPROVED",false);
        article(23,"generic","","<script><h1>quasar</h1></script><p>nothing</p>","APPROVED",false);
        var ids=ids("quasar",false);assertEquals(3,ids.size());assertEquals(22L,ids.get(2));assertFalse(ids.contains(23L));
    }
}
