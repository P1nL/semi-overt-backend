import java.sql.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Local acceptance fixture only. Refuses non-isolated schemas and never prints credentials. */
public class S3DatabaseFixture {
    public static void main(String[] args) throws Exception {
        String db=System.getenv("S3_ACCEPTANCE_DB");
        if(db==null || !db.matches("s3_accept_[a-f0-9]{16}"))throw new IllegalArgumentException("Unsafe schema");
        String base="jdbc:mysql://127.0.0.1:13306/";
        String password=System.getenv("S3_MYSQL_PASSWORD");
        if(args.length==1 && args[0].equals("create")) {
            try(var c=DriverManager.getConnection(base,"root",password);var s=c.createStatement()) {
                s.execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            return;
        }
        try(var c=DriverManager.getConnection(base+db+"?serverTimezone=Asia/Shanghai","root",password)) {
            if(args[0].equals("seed")) {
                String hash=new BCryptPasswordEncoder().encode(System.getenv("S3_LOGIN_PASSWORD"));
                try(var p=c.prepareStatement("INSERT INTO users(id,username,nickname,email,password,role) VALUES(?,?,?,?,?,?)")) {
                    String[] names={"s3writer","s3visitor","s3admin","s3otheradmin","s3adminauthor"};
                    for(int i=0;i<names.length;i++) {
                        p.setLong(1,i+101);p.setString(2,names[i]);p.setString(3,names[i]);
                        p.setString(4,names[i]+"@example.invalid");p.setString(5,hash);p.setString(6,i<2?"USER":"ADMIN");p.addBatch();
                    }
                    p.executeBatch();
                }
                return;
            }
            if(args[0].equals("lose-projection") && args.length==2) {
                long articleId=Long.parseLong(args[1]);
                // Explicit fault injection only in a randomized acceptance schema.
                try(var p=c.prepareStatement("DELETE FROM review_tasks WHERE article_id=?")) {
                    p.setLong(1,articleId);p.executeUpdate();
                }
                return;
            }
            if(args[0].equals("seed-draft-limit")) {
                try(var p=c.prepareStatement("INSERT INTO articles(author_id,title,status,draft_visible,version) VALUES(102,?,'DRAFT',false,0)")) {
                    for(int i=0;i<99;i++){p.setString(1,"limit fixture "+i);p.addBatch();}
                    p.executeBatch();
                }
                return;
            }
            if(!args[0].equals("query") || args.length!=2 || !args[1].stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT ")
                    || args[1].contains(";"))throw new IllegalArgumentException("Read-only query required");
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
