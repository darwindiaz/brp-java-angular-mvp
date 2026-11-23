package co.brp.identity.presentation;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;

@RestControllerAdvice(assignableTypes = {AuthController.class, UserAdministrationController.class})
class IdentityErrorHandler {

    private static final Set<String> INPUT_FIELDS = Set.of("login", "password", "name", "role", "active", "id", "module");

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleBusinessError(ResponseStatusException exception) {
        return exception.getBody();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(safeField(error.getField()), switch (error.getCode()) {
                    case "NotBlank", "NotNull" -> "Campo obligatorio.";
                    case "Size" -> "Longitud fuera del rango permitido.";
                    case null, default -> "Valor inválido.";
                }))
                .distinct()
                .toList();
        return badRequest(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        var path = exception.getCause() instanceof JacksonException jackson ? jackson.getPath() : List.<JacksonException.Reference>of();
        var field = path.isEmpty() ? "body" : safeField(path.getFirst().getPropertyName());
        return badRequest(List.of(new FieldViolation(field, "Contenido o formato inválido.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return badRequest(List.of(new FieldViolation(safeField(exception.getName()), "Formato inválido.")));
    }

    private static String safeField(String name) {
        return name != null && INPUT_FIELDS.contains(name) ? name : "body";
    }

    private static ProblemDetail badRequest(List<FieldViolation> errors) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private record FieldViolation(String field, String message) {
    }
}
