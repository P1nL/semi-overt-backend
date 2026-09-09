package com.platform.auth.acceptance;

import com.platform.auth.controller.AuthController;
import com.platform.auth.internal.SessionValidationController;
import com.platform.auth.internal.RequestBudgetController;
import com.platform.auth.config.SecurityConfig;
import com.platform.auth.service.TurnstileService;
import com.platform.auth.session.DeviceSessionService;
import com.platform.web.support.config.CommonJacksonConfig;
import com.platform.web.support.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="S2_MYSQL_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/")
@SpringBootTest(classes=AuthAcceptanceTest.App.class,properties={"spring.config.location=optional:classpath:/s2-test-empty.yml",
 "spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.config.import-check.enabled=false","spring.cloud.nacos.discovery.enabled=false",
 "spring.cloud.discovery.enabled=false","platform.internal.token=s2-test-only-internal","platform.auth.refresh-cookie-secure=false",
 "platform.cors.allowed-origins=http://localhost:5173","platform.mail.enabled=true","spring.mail.username=test@example.invalid",
 "platform.auth.registration-code-required=true","platform.auth.reset-code-pepper=s2-test-only-code-pepper","jwt.token.sign-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
class AuthAcceptanceTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan("com.platform.auth.mapper")
    @ComponentScan(basePackages={"com.platform.auth.session","com.platform.auth.repository","com.platform.auth.security"})
    @Import({AuthController.class,SessionValidationController.class,RequestBudgetController.class,SecurityConfig.class,CommonJacksonConfig.class,GlobalExceptionHandler.class,com.platform.auth.service.impl.AuthServiceImpl.class,com.platform.auth.service.JdbcMailBudget.class,com.platform.auth.config.MybatisPlusConfig.class,com.platform.auth.config.MybatisPlusConfig.AutoFillHandler.class})
    static class App {}
    @MockBean TurnstileService turnstile;
    @MockBean JavaMailSender mail;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DeviceSessionService sessions;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) throws Exception {
        String base=System.getenv("S2_MYSQL_URL"),pass=System.getenv("S2_MYSQL_PASSWORD"),db="s2_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(base,"root",pass);var s=c.createStatement()){
            s.execute("CREATE DATABASE `"+db+"`");s.execute("USE `"+db+"`");
            String schema=Files.readString(Path.of("../db-migration/src/main/resources/db/reference/monolith-40edf51.sql"));
            for(String statement:schema.split(";"))if(!statement.isBlank())s.execute(statement);
            s.execute("ALTER TABLE users RENAME COLUMN password_hash TO password");
        }
        p.add("spring.datasource.url",()->base+db+"?serverTimezone=UTC");p.add("spring.datasource.username",()->"root");p.add("spring.datasource.password",()->pass);
    }
    String postJson(String path,String body,int status) throws Exception {
        return mvc.perform(post(path).header("Origin","http://localhost:5173").contentType("application/json").content(body))
            .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }
    String sentCode(){var cap=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail,atLeastOnce()).send(cap.capture());String text=cap.getAllValues().get(cap.getAllValues().size()-1).getText();var m=java.util.regex.Pattern.compile("(?<!\\d)\\d{6}(?!\\d)").matcher(text);assertTrue(m.find());return m.group();}
    @Test void realDatabaseCookieRefreshReplayResetAndOrigin() throws Exception {
        postJson("/api/v1/auth/register-code","{\"email\":\"member@example.invalid\",\"cfTurnstileToken\":\"test\"}",200);
        String code=sentCode();
        String registration="{\"username\":\"member01\",\"email\":\"member@example.invalid\",\"password\":\"ValidPass123\",\"emailCode\":\""+code+"\",\"cfTurnstileToken\":\"test\"}";
        postJson("/api/v1/auth/register",registration,200);
        var login=mvc.perform(post("/api/v1/auth/login").header("Origin","http://localhost:5173").contentType("application/json")
            .content("{\"account\":\"member01\",\"password\":\"ValidPass123\",\"rememberMe\":false}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertFalse(login.getHeader("Set-Cookie").contains("Max-Age="));assertTrue(login.getHeader("Set-Cookie").contains("HttpOnly"));
        Cookie old=login.getCookie("semi_overt_refresh");assertNotNull(old);
        String access=json.readTree(login.getContentAsString()).path("data").path("token").asText();assertNotNull(sessions.validateAccess(access));
        var refreshed=mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isOk()).andReturn().getResponse();
        assertFalse(refreshed.getHeader("Set-Cookie").contains("Max-Age="));assertNotEquals(old.getValue(),refreshed.getCookie("semi_overt_refresh").getValue());
        mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isUnauthorized());assertNull(sessions.validateAccess(access));
        mvc.perform(post("/api/v1/auth/refresh").header("Origin","https://evil.invalid")).andExpect(status().isForbidden());
        mvc.perform(post("/internal/auth/session/validate").contentType("application/json").content("{\"token\":\"bad\"}")).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM rate_limit_buckets");
        postJson("/api/v1/auth/forgot-password","{\"email\":\"member@example.invalid\"}",200);
        String reset=sentCode();postJson("/api/v1/auth/reset-password","{\"email\":\"member@example.invalid\",\"code\":\""+reset+"\",\"newPassword\":\"NewValidPass123\"}",200);
        assertEquals(1,jdbc.queryForObject("SELECT session_version FROM users WHERE username='member01'",Integer.class));
        postJson("/api/v1/auth/reset-password","{\"email\":\"member@example.invalid\",\"code\":\""+reset+"\",\"newPassword\":\"AgainPass123\"}",400);
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk());
    }
    @Test void badRegistrationCodesCountDurablyAndMailBudgetIsShared() throws Exception {
        jdbc.update("DELETE FROM rate_limit_buckets");
        postJson("/api/v1/auth/register-code","{\"email\":\"attempts@example.invalid\",\"cfTurnstileToken\":\"test\"}",200);
        String correct=sentCode(),wrong=correct.equals("000000")?"111111":"000000";
        String base="{\"username\":\"attempts01\",\"email\":\"attempts@example.invalid\",\"password\":\"ValidPass123\",\"cfTurnstileToken\":\"test\",\"emailCode\":\"";
        for(int i=0;i<5;i++)postJson("/api/v1/auth/register",base+wrong+"\"}",400);
        assertEquals(5,jdbc.queryForObject("SELECT attempts FROM email_verification_codes WHERE email='attempts@example.invalid' ORDER BY id DESC LIMIT 1",Integer.class));
        postJson("/api/v1/auth/register",base+correct+"\"}",400);
        mvc.perform(post("/api/v1/auth/forgot-password").header("Origin","http://localhost:5173").contentType("application/json").content("{\"email\":\"attempts@example.invalid\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
    @Test void rememberedCookieSurvivesRefreshAndLogoutRevokesAccess() throws Exception {
        String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("ValidPass123");
        jdbc.update("INSERT INTO users(username,email,password,role,session_version) VALUES('persistent01','persistent@example.invalid',?,'USER',0)",hash);
        var response=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"account\":\"persistent01\",\"password\":\"ValidPass123\",\"rememberMe\":true}"))
            .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age="))).andReturn().getResponse();
        var refreshed=mvc.perform(post("/api/v1/auth/refresh").cookie(response.getCookie("semi_overt_refresh")))
            .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age="))).andReturn().getResponse();
        String access=json.readTree(refreshed.getContentAsString()).path("data").path("token").asText();
        assertNotNull(sessions.validateAccess(access));
        mvc.perform(post("/api/v1/auth/logout").cookie(refreshed.getCookie("semi_overt_refresh"))).andExpect(status().isOk());
        assertNull(sessions.validateAccess(access));
    }}
