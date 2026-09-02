package com.ice.reviewservice.Exception;

public class AlreadyReportedException extends RuntimeException {
    public AlreadyReportedException(String message) {
        super(message);
    }
}
