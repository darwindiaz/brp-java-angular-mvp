package co.brp.offers.presentation;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import co.brp.offers.application.OfferAttachments;
import co.brp.offers.application.OfferAttachments.Metadata;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/offers/{number}/attachments")
@PreAuthorize("isAuthenticated() and principal.canAccessOfferAttachments()")
class OfferAttachmentController {

    private final OfferAttachments attachments;

    OfferAttachmentController(OfferAttachments attachments) {
        this.attachments = attachments;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    List<Metadata> upload(@PathVariable long number, @RequestPart("files") List<MultipartFile> files) {
        return attachments.upload(number, files);
    }

    @GetMapping
    List<Metadata> list(@PathVariable long number) {
        return attachments.list(number);
    }

    @GetMapping("/{attachmentId}")
    ResponseEntity<InputStreamResource> download(@PathVariable long number, @PathVariable UUID attachmentId) {
        var download = attachments.download(number, attachmentId);
        var metadata = download.metadata();
        var disposition = ContentDisposition.attachment()
                .filename(metadata.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.content()));
    }
}
