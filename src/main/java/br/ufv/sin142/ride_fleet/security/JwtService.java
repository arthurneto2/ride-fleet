package br.ufv.sin142.ride_fleet.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String EMAIL_CLAIM = "email";

    public static final String ROLE_PASSENGER = "ROLE_PASSENGER";
    public static final String ROLE_DRIVER = "ROLE_DRIVER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_CORE = "ROLE_CORE";

    // 24 horas em milissegundos
    private static final long PASSENGER_TOKEN_EXPIRATION = 24 * 60 * 60 * 1000L;
    // 12 horas em milissegundos
    private static final long DRIVER_TOKEN_EXPIRATION = 12 * 60 * 60 * 1000L;
    // 8 horas: e a credencial mais privilegiada, logo a de vida mais curta
    private static final long ADMIN_TOKEN_EXPIRATION = 8 * 60 * 60 * 1000L;

    private final String secretKey;

    /**
     * Segredo recebido pelo construtor, e nao injetado em campo, para que o
     * servico seja construivel em teste unitario sem contexto Spring.
     */
    public JwtService(@Value("${security.jwt.secret:minhasenhasecretasupersecreta1234567890}") String secretKey) {
        this.secretKey = secretKey;
    }

    public String generatePassengerToken(UUID id, String email) {
        return generateToken(id.toString(), email, ROLE_PASSENGER, PASSENGER_TOKEN_EXPIRATION);
    }

    public String generateDriverToken(UUID id, String email) {
        return generateToken(id.toString(), email, ROLE_DRIVER, DRIVER_TOKEN_EXPIRATION);
    }

    public String generateAdminToken(UUID id, String email) {
        return generateToken(id.toString(), email, ROLE_ADMIN, ADMIN_TOKEN_EXPIRATION);
    }

    private String generateToken(String subject, String email, String role, long expirationMillis) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.create()
                .withSubject(subject)
                .withClaim(EMAIL_CLAIM, email)
                .withClaim(ROLE_CLAIM, role)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMillis))
                .sign(algorithm);
    }

    public DecodedJWT validateToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.require(algorithm)
                .build()
                .verify(token);
    }

    public String getSubject(DecodedJWT decodedJWT) {
        return decodedJWT.getSubject();
    }

    public String getRole(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim(ROLE_CLAIM).asString();
    }

    public String getEmail(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim(EMAIL_CLAIM).asString();
    }
}
