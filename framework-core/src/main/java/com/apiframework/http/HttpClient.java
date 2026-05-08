package com.apiframework.http;

import com.apiframework.model.ApiResponse;

import java.util.Map;

/**
 * Fluent HTTP client interface. Implemented by {@link RestAssuredHttpClient}.
 * This interface defines the API surface for domain modules — it is not an abstraction
 * boundary for swapping HTTP libraries. REST Assured is a core framework dependency.you
 */
public interface HttpClient {

    <T> ApiResponse<T> get(String path, Class<T> responseType);

    <T> ApiResponse<T> get(String path, Map<String, ?> queryParams, Class<T> responseType);

    <T> ApiResponse<T> post(String path, Object body, Class<T> responseType);

    <T> ApiResponse<T> put(String path, Object body, Class<T> responseType);

    <T> ApiResponse<T> patch(String path, Object body, Class<T> responseType);

    <T> ApiResponse<T> delete(String path, Class<T> responseType);
}
