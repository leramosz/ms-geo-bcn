package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.advice;

import com.geobcn.service.infrastructure.generated.openapi.model.ErrorMessageInner;
import com.geobcn.service.infrastructure.generated.openapi.model.ErrorMessages;
import com.geobcn.service.infrastructure.generated.openapi.model.ErrorMessageInnerParamsInner;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String MISSING_HEADER_ALIAS = "missingHeader";
    public static final String MISSING_HEADER_MSG = "Missing mandatory header.";
    public static final String CONSTRAINT_VIOLATION_ALIAS = "constraintViolation";
    public static final String CONSTRAINT_VIOLATION_MSG = "Parameter constraint violation.";
    public static final String MESSAGE_CLEANING_REGEX = "^(getDrinkingWaterFountainsDrinkingWaterFountainId\\." +
            "|getDrinkingWaterFountains\\.)";

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorMessages> missingRequestHeaderException(MissingRequestHeaderException ex) {
        return new ResponseEntity<>(getErrorMessages(MISSING_HEADER_ALIAS, MISSING_HEADER_MSG,
                ErrorMessageInner.TypeEnum.ERROR, ex.getHeaderName(), ex.getBody().getDetail()),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorMessages> constraintViolationException(ConstraintViolationException ex) {
        String paramMessage = ex.getMessage().replaceFirst(MESSAGE_CLEANING_REGEX, "");
        String paramName = paramMessage.substring(0, paramMessage.indexOf(":"));
        return new ResponseEntity<>(getErrorMessages(CONSTRAINT_VIOLATION_ALIAS, CONSTRAINT_VIOLATION_MSG,
                ErrorMessageInner.TypeEnum.ERROR, paramName, paramMessage), HttpStatus.BAD_REQUEST);
    }

    private static ErrorMessages getErrorMessages(String alias, String message, ErrorMessageInner.TypeEnum error,
                                                  String paramName, String paramMessage) {

        ErrorMessageInner errorMessageInner = new ErrorMessageInner();
        errorMessageInner.alias(alias);
        errorMessageInner.message(message);
        errorMessageInner.type(error);

        ErrorMessageInnerParamsInner errorMessageInnerParamsInner = new ErrorMessageInnerParamsInner();
        errorMessageInnerParamsInner.name(paramName);
        errorMessageInnerParamsInner.message(paramMessage);

        errorMessageInner.params(List.of(errorMessageInnerParamsInner));

        ErrorMessages errorMessages = new ErrorMessages();
        errorMessages.addMessagesItem(errorMessageInner);

        return errorMessages;
    }

}
