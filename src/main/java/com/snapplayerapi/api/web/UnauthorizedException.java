package com.snapplayerapi.api.web;

/**
 * Signals that the request could not be authenticated for a private API route.
 *
 * <p>This project does not use Spring Security yet, so a lightweight domain exception keeps the
 * controller/service code simple while still producing a consistent HTTP 401 response.</p>
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
