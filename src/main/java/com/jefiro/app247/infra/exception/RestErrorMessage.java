package com.jefiro.app247.infra.exception;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RestErrorMessage {
    private HttpStatus status;
    private String message;
    private String code;
    private Map<String, String> fieldErrors;

    public RestErrorMessage(HttpStatus status, String message) {
        this(status, message, null, null);
    }
}
