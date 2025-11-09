package com.ganzithon.homemate.exception;

import com.ganzithon.homemate.dto.MessageResponse;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = Objects.requireNonNullElse(ex.getMessage(), "요청을 처리할 수 없습니다.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponse(message));
    }
}

