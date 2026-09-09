package com.platform.auth.session;

import com.platform.auth.model.RefreshTokenRotation;
import com.platform.auth.repository.DeviceSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/** Outcomes are returned after commit. The HTTP caller must map a failure outside the transaction. */
@Service
public class DeviceSessionService {
    public enum Status { ISSUED, UNKNOWN, REPLAYED, EXPIRED, USER_MISSING }
    public record Tokens(String accessToken,String refreshToken,String sessionId,long userId,
                         boolean persistent,LocalDateTime absoluteExpiresAt) {
        @Override public String toString(){return "Tokens[redacted]";}
    }
    public record Outcome(Status status,Tokens tokens) {}
    private record User(long id,String username,String role,long version) {}
    private final DeviceSessionRepository sessions;
    private final JdbcTemplate jdbc;
    private final SessionAccessTokenIssuer issuer;
    private final TransactionTemplate tx;
    private final long idleDays,absoluteDays;
    private final SecureRandom random=new SecureRandom();
    public DeviceSessionService(DeviceSessionRepository sessions,JdbcTemplate jdbc,
                                SessionAccessTokenIssuer issuer,PlatformTransactionManager transactions,
                                @Value("${platform.auth.refresh-idle-days:30}") long idleDays,
                                @Value("${platform.auth.refresh-absolute-days:90}") long absoluteDays) {
        if(idleDays<=0 || absoluteDays>90 || idleDays>absoluteDays) throw new IllegalArgumentException("Invalid refresh windows");
        this.sessions=sessions;this.jdbc=jdbc;this.issuer=issuer;this.idleDays=idleDays;this.absoluteDays=absoluteDays;
        tx=new TransactionTemplate(transactions);
        // This durable boundary must survive later HTTP exception translation, including an outer transaction.
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    /** Call only after password/registration checks have committed; never trust a client-supplied userId. */
    public Outcome openForAuthenticatedUser(long userId,boolean persistent,String previousRefresh) {
        return tx.execute(s->{
            User user=user(userId);if(user==null)return new Outcome(Status.USER_MISSING,null);
            LocalDateTime now=LocalDateTime.now();
            if(validFormat(previousRefresh)) sessions.revokeByRefreshToken(hash(previousRefresh),now);
            String token=randomToken(), id=UUID.randomUUID().toString(),family=UUID.randomUUID().toString();
            LocalDateTime absolute=now.plusDays(absoluteDays);
            sessions.createSession(id,userId,family,hash(token),now,now.plusDays(idleDays),absolute,persistent);
            return issued(user,id,token,persistent,absolute);
        });
    }
    public Outcome refresh(String token) {
        if(!validFormat(token))return new Outcome(Status.UNKNOWN,null);
        return tx.execute(s->{
            LocalDateTime now=LocalDateTime.now();String replacement=randomToken();
            var rotation=sessions.rotateRefreshToken(hash(token),hash(replacement),now,now.plusDays(idleDays));
            if(rotation.status()!=RefreshTokenRotation.Status.ROTATED)
                return new Outcome(Status.valueOf(rotation.status().name()),null);
            User user=user(rotation.userId());
            if(user==null){sessions.revokeSession(rotation.sessionId(),now);return new Outcome(Status.USER_MISSING,null);}
            return issued(user,rotation.sessionId(),replacement,rotation.persistent(),rotation.absoluteExpiresAt());
        });
    }
    public void logout(String token) {
        if(validFormat(token)) tx.executeWithoutResult(s->sessions.revokeByRefreshToken(hash(token),LocalDateTime.now()));
    }
    private Outcome issued(User user,String id,String refresh,boolean persistent,LocalDateTime absolute) {
        return new Outcome(Status.ISSUED,new Tokens(issuer.issue(user.id(),user.username(),user.role(),id,user.version()),refresh,id,user.id(),persistent,absolute));
    }
    private User user(long id) {
        return jdbc.query("SELECT id,username,role,session_version FROM users WHERE id=?",
                (r,n)->new User(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4)),id).stream().findFirst().orElse(null);
    }
    private String randomToken(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private boolean validFormat(String token){return token!=null && token.matches("[A-Za-z0-9_-]{43}");}
    static String hash(String token) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
}
