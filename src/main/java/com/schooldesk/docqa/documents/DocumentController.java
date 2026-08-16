package com.schooldesk.docqa.documents;

import com.schooldesk.docqa.ingestion.DocumentUploadService;
import com.schooldesk.docqa.ingestion.DocumentUploadService.UploadOutcome;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController {

    private final DocumentUploadService uploads;

    DocumentController(DocumentUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category) {

        UploadOutcome outcome = uploads.accept(file, title, category);
        DocumentResponse body = DocumentResponse.from(outcome.document());

        return ResponseEntity.status(outcome.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(body);
    }
}
