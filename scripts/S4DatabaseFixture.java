import java.sql.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit synthetic fixtures and read-only receipts, never a general database write console. */
public class S4DatabaseFixture {
    public static void main(String[] args) throws Exception {
        String db=System.getenv("S4_ACCEPTANCE_DB");
        if(db==null || !db.matches("s4_accept_[a-f0-9]{16}")) throw new IllegalArgumentException("Unsafe schema");
        String base="jdbc:mysql://127.0.0.1:13306/", password=System.getenv("S4_MYSQL_PASSWORD");
        if(args.length==1 && args[0].equals("create")) {
            try(var c=DriverManager.getConnection(base,"root",password);var s=c.createStatement()) {
                s.execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            return;
        }
        try(var c=DriverManager.getConnection(base+db+"?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",password)) {
            if(args.length==1 && args[0].equals("seed")) {
                String hash=new BCryptPasswordEncoder().encode(System.getenv("S4_LOGIN_PASSWORD"));
                try(var p=c.prepareStatement("INSERT INTO users(id,username,nickname,email,password,role,signature) VALUES(?,?,?,?,?,?,?)")) {
                    String[] names={"s4writer","s4visitor","s4admin","s4otheradmin"};
                    for(int i=0;i<names.length;i++) {
                        p.setLong(1,101+i);p.setString(2,names[i]);p.setString(3,i==0?"星云作者":names[i]);
                        p.setString(4,names[i]+"@example.invalid");p.setString(5,hash);p.setString(6,i<2?"USER":"ADMIN");
                        p.setString(7,i==0?"nebula profile fixture":"synthetic fixture");p.addBatch();
                    }
                    p.executeBatch();
                }
                try(var p=c.prepareStatement("INSERT INTO articles(id,author_id,title,content,summary,status,word_count,read_minutes,duration_category,version,published_at,updated_at,deleted,submission_id) VALUES(?,101,?,?,?,?,?,?,?,0,IF(?='APPROVED',NOW(),NULL),DATE_SUB(NOW(),INTERVAL ? DAY),?,?)")) {
                    for(int i=0;i<27;i++) {
                        String status=i<23?"APPROVED":i==23?"DRAFT":i==24?"RETURNED":i==25?"PENDING":"REJECTED";
                        String title=i==0?"nebula":i==1?"nebula guide":"公开阅读 "+i;
                        String content=i==2?"<p>正文 nebula 探索</p>":i==3?"<script>hiddenphantom</script><p>safe body</p>":i==4?"[Visible anchor](https://hiddenurlphantom.invalid)":"用于首页日历与搜索的合成测试文章。";
                        if(i>=23){title="privatephantom "+i;content="privatephantom 私有内容";}
                        p.setLong(1,1001+i);p.setString(2,title);p.setString(3,content);p.setString(4,"fixture summary "+i);
                        p.setString(5,status);p.setInt(6,100+i);p.setDouble(7,i<18?1:i<20?6:15);
                        p.setString(8,i<18?"QUICK":i<20?"SHORT":"DEEP");p.setString(9,status);
                        p.setInt(10,i==21?1200:i>=23?1:0);p.setBoolean(11,i==22);p.setString(12,status.equals("PENDING")?"s4-fixture-pending":null);p.addBatch();
                    }
                    p.executeBatch();
                }
                try(var s=c.createStatement()) {
                    s.executeUpdate("INSERT INTO notifications(id,user_id,type,title,content,biz_id,decision_id,created_at) VALUES(9001,101,'REVIEW','历史通知','未知关联历史原文',NULL,NULL,DATE_SUB(NOW(),INTERVAL 1 DAY)),(9002,102,'REVIEW','其他用户通知','不得泄露',NULL,NULL,NOW())");
                }
                return;
            }
            if(args.length==1 && args[0].equals("enable-fulltext")) {
                try(var s=c.createStatement()){s.execute("CREATE FULLTEXT INDEX ft_articles_search ON articles(title,summary,content) WITH PARSER ngram");}
                return;
            }
            if(args.length==1 && args[0].equals("disable-fulltext")) {
                try(var s=c.createStatement()){s.execute("ALTER TABLE articles DROP INDEX ft_articles_search");}
                return;
            }
            if(!args[0].equals("query") || args.length!=2 || !args[1].stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT ") || args[1].contains(";")) {
                throw new IllegalArgumentException("Read-only query or named fixture action required");
            }
            try(var s=c.createStatement();var r=s.executeQuery(args[1])) {
                var metadata=r.getMetaData();
                while(r.next()) {
                    var row=new ArrayList<String>();
                    for(int i=1;i<=metadata.getColumnCount();i++)row.add(Objects.toString(r.getObject(i),"NULL"));
                    System.out.println(String.join("\t",row));
                }
            }
        }
    }
}
