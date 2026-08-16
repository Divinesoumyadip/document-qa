package com.schooldesk.docqa.web;

import org.springframework.http.HttpStatus;

public class ModelProviderException extends ApiException {

    public ModelProviderException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "The answering service is temporarily unavailable. Please try again shortly.");
    }
}
