package com.schooldesk.docqa.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Extends ResponseEntityExceptionHandler deliberately.
 *
 * Without it, the catch-all @ExceptionHandler(Exception.class) below runs
 * before Spring's DefaultHandlerExceptionResolver and swallows the framework's
 * own exceptions -- a blank question, a missing file part, malformed JSON and
 * an unknown path would all come back as 500 instead of 400/404/415, and the
 * @Valid annotations on the request records would be decorative.
 * ResponseEntityExceptionHandler's handlers are more specific, so they win.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception errorId={}", errorId, ex);

        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        body.setTitle("Internal Server Error");
        body.setDetail("The request could not be completed. Quote reference %s.".formatted(errorId));
        return ResponseEntity.internalServerError().body(body);
    }

    /**
     * Framework exceptions (validation, unreadable body, unsupported media type,
     * no handler found) arrive here. Kept to a bare ProblemDetail so nothing
     * internal leaks, while preserving the correct status code.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatus(statusCode);
        problem.setTitle(HttpStatus.valueOf(statusCode.value()).getReasonPhrase());
        problem.setDetail(safeDetail(statusCode));
        return new ResponseEntity<>(problem, headers, statusCode);
    }

    private String safeDetail(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return "The request was not valid. Check required fields and body format.";
        }
        if (statusCode.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
            return "Unsupported content type for this endpoint.";
        }
        if (statusCode.value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return "The file exceeds the maximum upload size of 20 MB.";
        }
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            return "No such endpoint.";
        }
        return "The request could not be completed.";
    }

    private ProblemDetail problem(ApiException ex) {
        ProblemDetail body = ProblemDetail.forStatus(ex.status());
        body.setTitle(ex.title());
        body.setDetail(ex.getMessage());
        return body;
    }
}
