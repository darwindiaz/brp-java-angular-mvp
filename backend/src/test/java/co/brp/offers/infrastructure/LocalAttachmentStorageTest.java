package co.brp.offers.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAttachmentStorageTest {

    @TempDir Path directory;

    @Test
    void neverOverwritesOrRemovesAPreexistingFile() throws IOException {
        var storage = new LocalAttachmentStorage(directory.toString());
        var id = UUID.randomUUID();
        Files.write(directory.resolve(id.toString()), new byte[]{1});

        assertThatThrownBy(() -> storage.write(id, new ByteArrayInputStream(new byte[]{2}), 1))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readAllBytes(directory.resolve(id.toString()))).containsExactly((byte) 1);
    }

    @Test
    void removesAnIncompleteWrite() {
        var storage = new LocalAttachmentStorage(directory.toString());
        var id = UUID.randomUUID();

        assertThatThrownBy(() -> storage.write(id, new ByteArrayInputStream(new byte[]{1}), 2))
                .isInstanceOf(IOException.class);
        assertThat(directory.resolve(id.toString())).doesNotExist();
    }
}
