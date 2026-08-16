package com.schooldesk.docqa.web;

import org.springframework.http.HttpStatus;

public class IngestionCapacityException extends ApiException {

    private final int retryAfterSeconds;

    public IngestionCapacityException(int retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                "Ingestion capacity is saturated. Retry shortly.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
