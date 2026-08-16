package com.schooldesk.docqa.web;

import org.springframework.http.HttpStatus;

public class UnsupportedDocumentTypeException extends ApiException {

    public UnsupportedDocumentTypeException() {

        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "Only PDF, DOCX, TXT and Markdown files are accepted.");
    }
}
