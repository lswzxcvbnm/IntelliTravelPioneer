package com.travel.intellitravel_pioneer.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class TokenUtil {
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;

    private final UserRepository userRepository;
    private final Algorithm algorithm;

    public TokenUtil(UserRepository userRepository,
                     @Value("${jwt.secret:IntelliTravel-Pioneer-JWT-Secret-Key-2026}") String jwtSecret) {
        this.userRepository = userRepository;
        this.algorithm = Algorithm.HMAC256(jwtSecret);
    }

    public String getToken(UserPO userPO) {
        Date date = new Date(System.currentTimeMillis() + EXPIRE_TIME);
        return JWT.create()
                .withAudience(String.valueOf(userPO.getId()))
                .withExpiresAt(date)
                .sign(algorithm);
    }

    public boolean verifyToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UserPO getUser(String token) {
        Integer userId = Integer.parseInt(JWT.decode(token).getAudience().get(0));
        return userRepository.findById(userId)
                .orElseThrow(IntellitravelException::userNotFound);
    }
}
