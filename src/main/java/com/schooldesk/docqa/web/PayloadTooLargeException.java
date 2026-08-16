package com.schooldesk.docqa.web;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                "The file exceeds the maximum upload size of 20 MB.");
    }
}
