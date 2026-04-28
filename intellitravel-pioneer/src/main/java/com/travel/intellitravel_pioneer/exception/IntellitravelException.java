package com.travel.intellitravel_pioneer.exception;

public class IntellitravelException extends RuntimeException {

    public IntellitravelException(String message) {
        super(message);
    }

    public static IntellitravelException userAlreadyExists() {
        return new IntellitravelException("User already exists");
    }

    public static IntellitravelException usernameOrPasswordError() {
        return new IntellitravelException("Incorrect username or password");
    }

    public static IntellitravelException userNotFound() {
        return new IntellitravelException("User not found");
    }
}
