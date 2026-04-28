package com.travel.intellitravel_pioneer.exception;

import com.travel.intellitravel_pioneer.vo.common.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IntellitravelException.class)
    public Response<String> handleIntellitravelException(IntellitravelException e) {
        e.printStackTrace();
        return Response.buildFailure(e.getMessage(), "500");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        // Return 503 without body to avoid HttpMessageNotWritableException for
        // SSE/Async requests
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalStateException(IllegalStateException e) {
        if (e.getMessage() != null && e.getMessage().startsWith("Failed to send")) {
            // SseEmitter客户端提前断开连接导致无法发送产生的异常
            // 直接返回空对应的正常状态码，防止污染日志以及避免抛出不可接受的媒体类型异常
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        e.printStackTrace();
        return Response.buildFailure(e.getMessage(), "500");
    }

    @ExceptionHandler(Exception.class)
    public Response<String> handleException(Exception e) {
        e.printStackTrace();
        return Response.buildFailure(e.getMessage(), "500");
    }
}
