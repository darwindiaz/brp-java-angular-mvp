package co.brp.offers.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Component
class AttachmentValidation {

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private final long maxBytes;

    AttachmentValidation(@Value("${brp.attachments.max-file-size}") String maxSize) {
        maxBytes = DataSize.parse(maxSize).toBytes();
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("El tamaño máximo de adjuntos debe ser positivo.");
        }
    }

    ValidatedFile validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }
        if (file.getSize() > maxBytes) {
            throw new OfferAttachments.TooLarge();
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255 || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Nombre de archivo inválido.");
        }

        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("El nombre del archivo debe incluir una extensión permitida.");
        }
        var format = switch (name.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "pdf" -> new Format("application/pdf", PDF);
            case "jpg", "jpeg" -> new Format("image/jpeg", JPEG);
            case "png" -> new Format("image/png", PNG);
            default -> throw new IllegalArgumentException("Formato de archivo no permitido.");
        };
        if (!format.contentType().equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("El tipo de contenido no coincide con la extensión.");
        }

        try (var input = file.getInputStream()) {
            if (!Arrays.equals(format.signature(), input.readNBytes(format.signature().length))) {
                throw new IllegalArgumentException("La firma del archivo no coincide con el formato.");
            }
        } catch (IOException e) {
            throw new OfferAttachments.StorageFailure(e);
        }
        return new ValidatedFile(file, name, format.contentType());
    }

    private record Format(String contentType, byte[] signature) {
    }

    record ValidatedFile(MultipartFile file, String filename, String contentType) {
    }
}
