package co.brp.offers.application;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentValidationTest {

    private final AttachmentValidation validation = new AttachmentValidation("10MB");

    @Test
    void acceptsAllowedExtensionMimeAndSignature() {
        var result = validation.validate(file("Informe.PDF", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertThat(result.filename()).isEqualTo("Informe.PDF");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../a.pdf", "dir\\a.pdf", "a\n.pdf", "a\u0000.pdf", "a.exe"})
    void rejectsPathNamesControlsAndOtherExtensions(String name) {
        assertThatThrownBy(() -> validation.validate(file(name, "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesTheConfiguredLimit() {
        var data = new byte[11];
        assertThatThrownBy(() -> new AttachmentValidation("10B").validate(file("a.pdf", "application/pdf", data)))
                .isInstanceOf(OfferAttachments.TooLarge.class);
    }

    private MockMultipartFile file(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("files", name, mime, bytes);
    }
}
