package com.hackathon.core.smartInhaler.exception;

import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Data
@ResponseStatus(value = HttpStatus.UNAUTHORIZED)
public class ApplicationException extends RuntimeException {

    private String errorMessage;

    public ApplicationException( String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
    }

}
