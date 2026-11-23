package co.brp.offers.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalAttachmentStorage {

    private static final Logger LOG = LoggerFactory.getLogger(LocalAttachmentStorage.class);

    private final Path root;

    public LocalAttachmentStorage(@Value("${brp.attachments.directory}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public void write(UUID id, InputStream input, long expectedSize) throws IOException {
        Files.createDirectories(root);
        var path = root.resolve(id.toString());
        // CREATE_NEW nunca sobrescribe ni adquiere propiedad de un archivo preexistente.
        var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (output) {
            if (input.transferTo(output) != expectedSize) {
                throw new IOException("El tamaño recibido cambió durante la escritura.");
            }
        } catch (IOException | RuntimeException failure) {
            try {
                delete(id);
            } catch (IOException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
                reportCleanupFailure(id);
            }
            throw failure;
        }
    }

    public InputStream open(UUID id) throws IOException {
        var path = root.resolve(id.toString());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("El contenido no está disponible.");
        }
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
    }

    public void delete(UUID id) throws IOException {
        Files.deleteIfExists(root.resolve(id.toString()));
    }

    public void reportCleanupFailure(UUID id) {
        LOG.error("No se pudo retirar el archivo de adjunto {}. Verificar almacenamiento y metadatos.", id);
    }
}
