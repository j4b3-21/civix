package com.civix.documents.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.civix.common.api.PageResponse;
import com.civix.common.api.Pagination;
import com.civix.common.security.CurrentUser;
import com.civix.documents.domain.DocumentCategory;
import com.civix.documents.dto.DocumentAccessDto;
import com.civix.documents.dto.DocumentDto;
import com.civix.documents.dto.ReviewRequest;
import com.civix.documents.service.DocumentService;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    public PageResponse<DocumentDto> list(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) DocumentCategory category) {
        return PageResponse.from(documents.list(CurrentUser.id(), category,
                Pagination.newestFirst(page, size, "uploadedAt")), x -> x);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(@RequestPart("file") MultipartFile file,
                                              @RequestParam("category") DocumentCategory category) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.upload(CurrentUser.id(), file, category));
    }

    @GetMapping("/{id}")
    public DocumentDto get(@PathVariable UUID id) {
        return documents.get(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id) {
        DocumentService.DocumentContent content = documents.content(CurrentUser.id(), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(content.stream()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documents.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/extract")
    public ResponseEntity<DocumentDto> extract(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documents.reextract(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/review")
    public DocumentDto review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return documents.review(CurrentUser.id(), id, request);
    }

    @GetMapping("/{id}/access-log")
    public List<DocumentAccessDto> accessLog(@PathVariable UUID id) {
        return documents.accessLog(CurrentUser.id(), id);
    }
}
