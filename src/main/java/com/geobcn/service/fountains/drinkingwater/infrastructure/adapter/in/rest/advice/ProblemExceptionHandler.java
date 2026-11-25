package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.advice;

import com.geobcn.service.fountains.drinkingwater.domain.FountainNotFoundException;
import com.geobcn.service.fountains.drinkingwater.domain.InvalidRequestException;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.InvalidParamDto;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.ProblemDto;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.MediaTypes;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.NotAcceptableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** Todos los errores salen como application/problem+json (RFC 9457) sin trazas. */
@RestControllerAdvice
public class ProblemExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDto> invalid(InvalidRequestException ex) {
        List<InvalidParamDto> params = ex.getInvalidParams().stream()
                .map(p -> new InvalidParamDto(p.name(), p.reason())).toList();
        return problem(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid parameter", ex.getMessage(), params);
    }

    @ExceptionHandler(FountainNotFoundException.class)
    ResponseEntity<ProblemDto> notFound(FountainNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDto> noResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", "Resource does not exist.", null);
    }

    @ExceptionHandler({NotAcceptableException.class, HttpMediaTypeNotAcceptableException.class})
    ResponseEntity<ProblemDto> notAcceptable(Exception ex) {
        String detail = ex instanceof NotAcceptableException
                ? ex.getMessage() : "Supported media types are application/json and application/geo+json.";
        return problem(HttpStatus.NOT_ACCEPTABLE, "not-acceptable", "Not acceptable", detail, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDto> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed",
                "Only GET is supported.", null);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ProblemDto> databaseDown(DataAccessResourceFailureException ex) {
        LOG.error("Database not available", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", "Service unavailable",
                "Operation not available at this time.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDto> unexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "We are sorry, something has gone wrong.", null);
    }

    private static ResponseEntity<ProblemDto> problem(HttpStatus status, String slug, String title, String detail,
                                                      List<InvalidParamDto> params) {
        return ResponseEntity.status(status).contentType(MediaTypes.PROBLEM_JSON)
                .body(new ProblemDto("/problems/" + slug, title, status.value(), detail, params));
    }
}
