package com.apiframework.testsupport.assertions;

import com.apiframework.model.ApiResponse;

/**
 * Entry point for the generic {@link ApiResponse} assertion DSL.
 *
 * <p>Usage:
 * <pre>{@code
 * ApiResponseAssert.assertThat(response)
 *     .hasStatus(200)
 *     .body()
 *         .field("args.key").isEqualTo("value")
 *     .matchesSchema("schemas/my-schema.json")
 *     .matchesSnapshot("my-snapshot");
 * }</pre>
 *
 * @param <T> the response body type
 */
@SuppressWarnings("unchecked")
public final class ApiResponseAssert<T>
        extends AbstractApiResponseAssert<ApiResponseAssert<T>, T> {

    private ApiResponseAssert(ApiResponse<T> actual) {
        // Raw cast is intentional: AssertJ's fluent chain requires the concrete type parameter.
        // The cast is safe because SELF is always ApiResponseAssert<T> here.
        super(actual, (Class) ApiResponseAssert.class);
    }

    public static <T> ApiResponseAssert<T> assertThat(ApiResponse<T> actual) {
        return new ApiResponseAssert<>(actual);
    }
}
