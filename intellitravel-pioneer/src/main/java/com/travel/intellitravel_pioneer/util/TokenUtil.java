package com.travel.intellitravel_pioneer.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class TokenUtil {
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;

    @Autowired
    UserRepository userRepository;

    public String getToken(UserPO userPO) {
        Date date = new Date(System.currentTimeMillis() + EXPIRE_TIME);
        return JWT.create()
                .withAudience(String.valueOf(userPO.getId()))
                .withExpiresAt(date)
                .sign(Algorithm.HMAC256(userPO.getPassword()));
    }

    public boolean verifyToken(String token) {
        try {
            Integer userId = Integer.parseInt(JWT.decode(token).getAudience().get(0));
            UserPO userPO = userRepository.findById(userId).get();
            JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(userPO.getPassword())).build();
            jwtVerifier.verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UserPO getUser(String token) {
        Integer userId = Integer.parseInt(JWT.decode(token).getAudience().get(0));
        return userRepository.findById(userId).get();
    }
}

