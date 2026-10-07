package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.uptrail.service.ClaimQueryService;
import com.uptrail.service.ClaimQueryService.DocumentFile;

/**
 * Authorised downloads of claim documents by id. Files are always sent as attachments with the detected
 * content type; the security headers tell the browser not to sniff them, and they are not cached.
 */
@Controller
public class ClaimDocumentController {

    private final ClaimQueryService queries;

    public ClaimDocumentController(ClaimQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/claims/documents/{id}")
    public ResponseEntity<Resource> staffDownload(@ModelAttribute(value = "user", binding = false) User user,
            @PathVariable Long id) {
        return download(queries.document(user, id, false));
    }

    @GetMapping("/admin/claims/documents/{id}")
    public ResponseEntity<Resource> adminDownload(@ModelAttribute(value = "user", binding = false) User user,
            @PathVariable Long id) {
        return download(queries.document(user, id, true));
    }

    private static ResponseEntity<Resource> download(DocumentFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }
}
