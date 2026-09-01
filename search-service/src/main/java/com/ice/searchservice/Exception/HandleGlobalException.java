package com.ice.searchservice.Exception;

import com.ice.searchservice.DTO.Response.Common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class HandleGlobalException {
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

    @ExceptionHandler(SearchQueryTooLongException.class)
    public ResponseEntity<ApiResponse<Void>> handleQueryTooLong(SearchQueryTooLongException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("Query quá dài (tối đa 200 ký tự).", "SEARCH_QUERY_TOO_LONG"));
    }

    @ExceptionHandler(InvalidSortOptionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSort(InvalidSortOptionException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("Sort option không hợp lệ.", "INVALID_SORT_OPTION"));
    }

    @ExceptionHandler(ElasticsearchUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleElasticsearchDown(ElasticsearchUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail("Elasticsearch — datastore của service — đang không khả dụng.", "ELASTICSEARCH_UNAVAILABLE"));
    }

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
}
