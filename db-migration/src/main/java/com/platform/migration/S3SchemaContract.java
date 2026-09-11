package com.platform.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Fail-closed S3 shape checks; never infer success from partially applied MySQL DDL. */
final class S3SchemaContract {
    static void require(Connection c, Map<String,SchemaContract.Table> tables) throws SQLException {
        var added=SchemaContract.parse(SchemaContract.resource("/db/migration/V4__s3_state_consistency.sql"));
        for(var entry:added.entrySet()) {
            var actual=tables.get(entry.getKey());
            if(actual==null || !actual.columns().equals(entry.getValue().columns())
                    || !actual.uniqueKeys().containsAll(entry.getValue().uniqueKeys())) {
                throw new IllegalStateException("S3 table contract mismatch: "+entry.getKey());
            }
        }
        column(tables,"articles","submission_id","varchar(64)",true);
        column(tables,"review_tasks","submission_id","varchar(64)",true);
        column(tables,"review_tasks","last_applied_version","bigint",false);
        column(tables,"review_tasks","decision_id","varchar(64)",true);
        column(tables,"review_tasks","command_state","varchar(16)",false);
        column(tables,"review_logs","decision_id","varchar(64)",true);
        column(tables,"review_logs","submission_id","varchar(64)",true);
        column(tables,"review_logs","article_version","bigint",true);
        column(tables,"notifications","decision_id","varchar(64)",true);
        for(String name:List.of("review_logs","notifications")) {
            if(!tables.get(name).uniqueKeys().contains(List.of("decision_id"))) throw new IllegalStateException("Missing S3 decision uniqueness: "+name);
        }
        SchemaContract.zero(c,"SELECT COUNT(*) FROM articles WHERE version<0 OR draft_visible<>0 OR (status='PENDING' AND submission_id IS NULL)","invalid S3 article version/privacy/submission");
        SchemaContract.zero(c,"SELECT COUNT(*) FROM content_review_decisions d LEFT JOIN articles a ON a.id=d.article_id WHERE a.id IS NULL OR d.state NOT IN ('FINAL','CONFLICT') OR (d.state='FINAL' AND (d.article_version IS NULL OR d.article_version>a.version))","invalid content decision receipt");
        SchemaContract.zero(c,"SELECT COUNT(*) FROM (SELECT article_id,submission_id FROM content_review_decisions WHERE state='FINAL' GROUP BY article_id,submission_id HAVING COUNT(*)>1) duplicates","multiple final decisions for submission");
    }
    private static void column(Map<String,SchemaContract.Table> tables,String table,String name,String type,boolean nullable) {
        if(!new SchemaContract.Column(type,nullable).equals(tables.get(table).columns().get(name))) {
            throw new IllegalStateException("S3 column contract mismatch: "+table+"."+name);
        }
    }
}
