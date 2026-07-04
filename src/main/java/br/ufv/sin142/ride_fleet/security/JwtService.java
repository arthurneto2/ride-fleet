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

    @Value("${security.jwt.secret:minhasenhasecretasupersecreta1234567890}")
    private String secretKey;

    private static final String ROLE_CLAIM = "role";
    private static final String EMAIL_CLAIM = "email";
    
    // 24 horas em milissegundos
    private static final long PASSENGER_TOKEN_EXPIRATION = 24 * 60 * 60 * 1000L;
    // 12 horas em milissegundos
    private static final long DRIVER_TOKEN_EXPIRATION = 12 * 60 * 60 * 1000L;

    public String generatePassengerToken(UUID id, String email) {
        return generateToken(id.toString(), email, "ROLE_PASSENGER", PASSENGER_TOKEN_EXPIRATION);
    }

    public String generateDriverToken(UUID id, String email) {
        return generateToken(id.toString(), email, "ROLE_DRIVER", DRIVER_TOKEN_EXPIRATION);
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
