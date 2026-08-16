package com.schooldesk.docqa.web;

import org.springframework.http.HttpStatus;

public class DocumentNotFoundException extends ApiException {

    public DocumentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Not Found", "No such document.");
    }
}
