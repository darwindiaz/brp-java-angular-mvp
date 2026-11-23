package co.brp.identity.presentation;

import co.brp.identity.application.IdentityAuthentication;
import co.brp.identity.application.IdentityAuthentication.AccessToken;
import co.brp.identity.domain.IdentityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final IdentityAuthentication authentication;

    AuthController(IdentityAuthentication authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/login")
    AccessToken login(@Valid @RequestBody Login request, HttpServletRequest servletRequest) {
        return authentication.login(request.login(), request.password(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    IdentityUser me(@AuthenticationPrincipal IdentityUser user) {
        return user;
    }

    record Login(@NotBlank @Size(max = 100) String login, @NotBlank @Size(max = 72) String password) {
        @Override
        public String toString() {
            return "Login[credentials=redacted]";
        }
    }
}
