package co.brp.offers.presentation;

import java.util.NoSuchElementException;

import co.brp.offers.application.OfferRegistration.BrpUnavailable;
import co.brp.offers.application.OfferAttachments.StorageFailure;
import co.brp.offers.application.OfferAttachments.TooLarge;
import co.brp.offers.domain.Offer.ForbiddenAction;
import co.brp.offers.domain.Offer.InvalidOffer;
import co.brp.offers.domain.Offer.TransitionConflict;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = {OfferController.class, OfferLifecycleController.class,
        OfferAttachmentController.class})
class OfferErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        var problem = invalidInput();
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), "Valor inválido.")).distinct().toList());
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class, IllegalArgumentException.class})
    ProblemDetail invalidInput() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.");
    }

    @ExceptionHandler({TooLarge.class, MaxUploadSizeExceededException.class})
    ProblemDetail attachmentTooLarge() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "La carga supera el tamaño permitido.");
    }

    @ExceptionHandler(StorageFailure.class)
    ProblemDetail attachmentStorageFailure() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo acceder al adjunto.");
    }

    @ExceptionHandler(InvalidOffer.class)
    ProblemDetail invalidOffer(InvalidOffer exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No existe el recurso solicitado.");
    }

    @ExceptionHandler(BrpUnavailable.class)
    ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El BRP vigente no está disponible con un precio válido.");
    }

    @ExceptionHandler(ForbiddenAction.class)
    ProblemDetail forbiddenAction() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "El usuario no puede realizar esta acción sobre la oferta.");
    }

    @ExceptionHandler(TransitionConflict.class)
    ProblemDetail transitionConflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El estado o la versión de la oferta no permite la transición.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El recurso se modificó durante la operación.");
    }

    private record FieldViolation(String field, String message) {
    }
}
