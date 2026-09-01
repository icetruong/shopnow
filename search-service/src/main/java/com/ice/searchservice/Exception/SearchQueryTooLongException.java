package com.ice.searchservice.Exception;

public class SearchQueryTooLongException extends RuntimeException {
    public SearchQueryTooLongException(String message) {
        super(message);
    }
}
