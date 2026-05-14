package com.qlda.userservice.Exception;

import com.qlda.userservice.DTO.Response.Common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {

        // Lấy message của field đầu tiên bị lỗi
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(message, "INVALID_REQUEST"));
    }

    @ExceptionHandler(EmailExistException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailExist(EmailExistException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(
                        "Email này đã được đăng ký",
                        "EMAIL_ALREADY_EXISTS"
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(IllegalArgumentException ex)
    {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage(), "INVALID_REQUEST"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), "NOT_FOUND"));
    }

    // authentication ném exception
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("Email hoặc mật khẩu không đúng", "INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Tài khoản đã bị vô hiệu hóa", "ACCOUNT_DISABLED"));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(LockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Tài khoản đã bị khóa", "ACCOUNT_LOCKED"));
    }

    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalAuth(InternalAuthenticationServiceException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Lỗi hệ thống, vui lòng thử lại sau", "INTERNAL_ERROR"));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex)
    {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ex.getMessage(), "REFRESH_TOKEN_INVALID"));
    }

    @ExceptionHandler(TokenResetPasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleExpiredTokenResetPassword(TokenResetPasswordException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), "RESET_TOKEN_INVALID"));
    }

    @ExceptionHandler(VerifyTokenInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerifyTokenInvalid(VerifyTokenInvalidException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), "VERIFICATION_TOKEN_INVALID"));
    }

    @ExceptionHandler(ChangePasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleChangePassword(ChangePasswordException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), "WRONG_PASSWORD"));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), "INVALID_FILE"));
    }
}
