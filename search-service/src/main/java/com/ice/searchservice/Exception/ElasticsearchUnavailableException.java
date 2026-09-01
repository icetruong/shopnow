package com.ice.searchservice.Exception;

public class ElasticsearchUnavailableException extends RuntimeException {
    public ElasticsearchUnavailableException(String message) {
        super(message);
    }
}
