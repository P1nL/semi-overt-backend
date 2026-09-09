package com.platform.auth.acceptance;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
/** Manual isolated acceptance bootstrap. Never packaged in the production JAR. */
public class AuthBrowserHarness {
    public static void main(String[] args) throws Exception {
        String base=System.getenv("S2_MYSQL_URL"),pass=System.getenv("S2_MYSQL_PASSWORD"),db="s2_"+UUID.randomUUID().toString().replace("-","");
        if(base==null||!base.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/"))throw new IllegalStateException("isolated loopback required");
        try(var c=DriverManager.getConnection(base,"root",pass);var s=c.createStatement()){
            s.execute("CREATE DATABASE `"+db+"`");s.execute("USE `"+db+"`");
            for(String statement:Files.readString(Path.of("../db-migration/src/main/resources/db/reference/monolith-40edf51.sql")).split(";"))if(!statement.isBlank())s.execute(statement);
            s.execute("ALTER TABLE users RENAME COLUMN password_hash TO password");
        }
        Map<String,Object> props=new HashMap<>();props.put("spring.config.location","optional:classpath:/s2-test-empty.yml");
        props.put("spring.datasource.url",base+db);props.put("spring.datasource.username","root");props.put("spring.datasource.password",pass);
        props.put("spring.cloud.nacos.config.enabled",false);props.put("spring.cloud.nacos.config.import-check.enabled",false);props.put("spring.cloud.nacos.discovery.enabled",false);props.put("spring.cloud.discovery.enabled",false);
        props.put("platform.internal.token","s2-test-only-internal");props.put("platform.auth.refresh-cookie-secure",false);props.put("platform.cors.allowed-origins","http://127.0.0.1:5188,http://localhost:5188");
        props.put("platform.auth.reset-code-pepper","s2-test-only-pepper");props.put("jwt.token.sign-key","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");props.put("server.port",18081);props.put("server.address","127.0.0.1");
        var app=new SpringApplication(AuthAcceptanceTest.App.class,Mocks.class);app.setDefaultProperties(props);var ctx=app.run();
        ctx.getBean(JdbcTemplate.class).update("INSERT INTO users(username,email,password,role,session_version) VALUES('browser01','browser@example.invalid',?,'USER',0)",new BCryptPasswordEncoder().encode("BrowserPass123"));
        System.out.println("S2_BROWSER_READY");
    }
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Import({com.platform.auth.controller.UserController.class,com.platform.auth.service.impl.UserServiceImpl.class})
    static class Mocks {
        @org.springframework.context.annotation.Bean com.platform.contract.content.client.ContentProfileClient content(){return org.mockito.Mockito.mock(com.platform.contract.content.client.ContentProfileClient.class);}
        @org.springframework.context.annotation.Bean com.platform.auth.service.TurnstileService turnstile(){return org.mockito.Mockito.mock(com.platform.auth.service.TurnstileService.class);}
        @org.springframework.context.annotation.Bean org.springframework.mail.javamail.JavaMailSender mail(){return org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);}
    }
}
