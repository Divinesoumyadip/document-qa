package com.schooldesk.docqa.web;

import org.springframework.http.HttpStatus;

public class EmptyUploadException extends ApiException {

    public EmptyUploadException() {
        super(HttpStatus.BAD_REQUEST, "Bad Request", "The uploaded file is empty.");
    }
}
