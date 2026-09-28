package com.uptrail.claim.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.uptrail.claim.service.ClaimQueryService;
import com.uptrail.claim.service.ClaimQueryService.DocumentFile;
import com.uptrail.identity.service.UptrailUserPrincipal;

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
    public ResponseEntity<Resource> staffDownload(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @PathVariable Long id) {
        return download(queries.document(principal.actor(), id, false));
    }

    @GetMapping("/admin/claims/documents/{id}")
    public ResponseEntity<Resource> adminDownload(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @PathVariable Long id) {
        return download(queries.document(principal.actor(), id, true));
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
