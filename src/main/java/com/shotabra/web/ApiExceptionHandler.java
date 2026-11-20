package com.shotabra.web;

import com.shotabra.game.GameException;
import com.shotabra.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {GameApiController.class, QrController.class})
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(GameException.class)
    ResponseEntity<ApiError> game(GameException e) {
        return ResponseEntity.status(e.error().status()).body(new ApiError(e.error().name(), e.error().message()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Revisa los datos e inténtalo de nuevo."));
    }

    /** Red de seguridad: una restricción de BD o un conflicto de bloqueo que se escape de GameService. */
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict(Exception e) {
        log.warn("Conflicto de concurrencia: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", "Alguien se te adelantó. Inténtalo de nuevo."));
    }
}
