package com.schooldesk.docqa.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IngestionCapacityException.class)
    ResponseEntity<ProblemDetail> handleCapacity(IngestionCapacityException ex) {
        return ResponseEntity.status(ex.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(problem(ex));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(problem(ex));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleTooLarge(MaxUploadSizeExceededException ex) {
        return handleApi(new PayloadTooLargeException());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {

        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception errorId={}", errorId, ex);

        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        body.setTitle("Internal Server Error");
        body.setDetail("The request could not be completed. Quote reference %s.".formatted(errorId));
        return ResponseEntity.internalServerError().body(body);
    }

    private ProblemDetail problem(ApiException ex) {
        ProblemDetail body = ProblemDetail.forStatus(ex.status());
        body.setTitle(ex.title());
        body.setDetail(ex.getMessage());
        return body;
    }
}
