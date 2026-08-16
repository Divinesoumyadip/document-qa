package com.schooldesk.docqa.documents;

import java.util.UUID;

import com.schooldesk.docqa.ingestion.DocumentUploadService;
import com.schooldesk.docqa.ingestion.DocumentUploadService.UploadOutcome;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController {

    private final DocumentUploadService uploads;
    private final DocumentQueryService queries;

    DocumentController(DocumentUploadService uploads, DocumentQueryService queries) {
        this.uploads = uploads;
        this.queries = queries;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category) {

        UploadOutcome outcome = uploads.accept(file, title, category);
        return ResponseEntity.status(outcome.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(DocumentResponse.from(outcome.document()));
    }

    @GetMapping
    ResponseEntity<Page<DocumentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(queries.list(PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/{id}")
    ResponseEntity<DocumentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(queries.get(id));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        queries.delete(id);
        return ResponseEntity.noContent().build();
    }
}
