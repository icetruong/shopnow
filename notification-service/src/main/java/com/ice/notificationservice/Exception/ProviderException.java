package com.ice.notificationservice.Exception;

/** Lỗi từ nhà cung cấp email/SMS/push (SMTP, Twilio, FCM...). */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
