package com.travel.intellitravel_pioneer.exception;

import com.travel.intellitravel_pioneer.vo.common.Response;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IntellitravelException.class)
    public Object handleIntellitravelException(IntellitravelException e, HttpServletRequest request) {
        if (isSseRequest(request)) {
            System.err.println("IntellitravelException during SSE: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        String msg = e.getMessage();
        HttpStatus httpStatus;

        if (msg != null && (msg.contains("already exists") || msg.contains("已存在"))) {
            httpStatus = HttpStatus.CONFLICT;
        } else if (msg != null && (msg.contains("Incorrect username") || msg.contains("username or password"))) {
            httpStatus = HttpStatus.UNAUTHORIZED;
        } else if (msg != null && (msg.contains("not found") || msg.contains("不存在"))) {
            httpStatus = HttpStatus.NOT_FOUND;
        } else {
            httpStatus = HttpStatus.BAD_REQUEST;
        }

        return ResponseEntity.status(httpStatus)
                .body(Response.buildFailure(msg, String.valueOf(httpStatus.value())));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        if (e.getMessage() != null && e.getMessage().startsWith("Failed to send")) {
            System.err.println("SSE client disconnected: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        
        if (isSseRequest(request)) {
            System.err.println("IllegalStateException during SSE: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.buildFailure(e.getMessage(), "500"));
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        if (isSseRequest(request)) {
            System.err.println("====== Unhandled Exception during SSE ======");
            System.err.println("Exception type: " + e.getClass().getName());
            System.err.println("Exception message: " + e.getMessage());
            e.printStackTrace();
            System.err.println("============================================");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        System.err.println("====== Unhandled Exception ======");
        System.err.println("Exception type: " + e.getClass().getName());
        System.err.println("Exception message: " + e.getMessage());
        e.printStackTrace();
        System.err.println("=================================");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.buildFailure(e.getMessage(), "500"));
    }
    
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return (accept != null && accept.contains("text/event-stream")) 
                || uri.contains("/stream");
    }
}
