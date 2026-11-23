package co.brp.identity.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record IdentityUser(long id, String name, String login, Role role, boolean active,
                           Instant createdAt, Instant updatedAt, List<Module> modules) {

    public IdentityUser {
        modules = List.copyOf(modules);
    }

    public boolean canAdminister() {
        return active && role == Role.ADMINISTRADOR && modules.contains(Module.ADMINISTRACION);
    }

    public boolean canRegisterOffers() {
        return active && role == Role.RADICADOR && modules.contains(Module.RADICACION);
    }

    public boolean canAnalyzeOffers() {
        return active && role == Role.ANALISTA && modules.contains(Module.ANALISIS);
    }

    public boolean canDecideOffers() {
        return active && role == Role.JEFE && modules.contains(Module.APROBACION);
    }

    public boolean canAccessOfferAttachments() {
        return switch (role) {
            case RADICADOR -> canRegisterOffers();
            case ANALISTA -> canAnalyzeOffers();
            case JEFE -> canDecideOffers();
            case ADMINISTRADOR -> active && modules.contains(Module.SEGUIMIENTO);
        };
    }

    public static String normalizeLogin(String login) {
        return login.strip().toLowerCase(Locale.ROOT);
    }

    public enum Role {
        ADMINISTRADOR, RADICADOR, ANALISTA, JEFE
    }

    public enum Module {
        RADICACION, ANALISIS, APROBACION, SEGUIMIENTO, ADMINISTRACION
    }
}
