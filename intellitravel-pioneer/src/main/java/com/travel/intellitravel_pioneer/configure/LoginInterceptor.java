package com.travel.intellitravel_pioneer.configure;

import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String REQUEST_USER_ATTR = "currentUser";

    private final TokenUtil tokenUtil;

    public LoginInterceptor(TokenUtil tokenUtil) {
        this.tokenUtil = tokenUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("token");
        if (token == null || token.isBlank()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"success\":false,\"message\":\"未提供认证token\"}");
            } catch (Exception ignored) {
            }
            return false;
        }

        if (!tokenUtil.verifyToken(token)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"success\":false,\"message\":\"token无效或已过期\"}");
            } catch (Exception ignored) {
            }
            return false;
        }

        try {
            UserPO user = tokenUtil.getUser(token);
            request.setAttribute(REQUEST_USER_ATTR, user);
        } catch (Exception e) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"success\":false,\"message\":\"用户不存在\"}");
            } catch (Exception ignored) {
            }
            return false;
        }

        return true;
    }
}
