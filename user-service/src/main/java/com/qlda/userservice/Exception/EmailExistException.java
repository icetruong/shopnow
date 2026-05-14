package com.qlda.userservice.Exception;

public class EmailExistException extends RuntimeException{
    public EmailExistException(String message)
    {
        super(message);
    }
}
