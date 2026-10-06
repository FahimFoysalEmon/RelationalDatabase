package org.example.relationaldatabase.exceptions;

public record ErrorModel(
        String error,
        int code
) {
}
