package co.brp.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import co.brp.identity.application.IdentityAdministration;
import co.brp.identity.domain.IdentityUser;
import co.brp.identity.domain.IdentityUser.Module;
import co.brp.identity.domain.IdentityUser.Role;
import co.brp.identity.infrastructure.IdentityStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityAdministrationTest {

    @Test
    void rejectsAnyMutationThatWouldLeaveNoEffectiveAdministrator() {
        var store = mock(IdentityStore.class);
        var administration = new IdentityAdministration(store, mock(PasswordEncoder.class));
        when(store.find(1L)).thenReturn(Optional.of(admin(1)));
        when(store.find(2L)).thenReturn(Optional.of(admin(2)));
        when(store.hasEffectiveAdministrator()).thenReturn(false);

        assertThatThrownBy(() -> administration.changeStatus(1, 2, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(store).lockAdministration();
        verify(store).changeStatus(2, false);
    }

    private static IdentityUser admin(long id) {
        return new IdentityUser(id, "Administrador", "admin-" + id, Role.ADMINISTRADOR,
                true, Instant.EPOCH, Instant.EPOCH, List.of(Module.ADMINISTRACION));
    }
}
