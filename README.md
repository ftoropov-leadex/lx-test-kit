# REST API Test Framework

Multi-module API test framework on Java 21 + Gradle + TestNG + REST Assured with domain separation, centralized retry, Allure reporting, and integration adapters.

## Modules

- `framework-core` — HTTP client (REST Assured), auth (Basic Auth), config, filters.
- `framework-test-support` — base test classes, retry analyzer, `NetworkAwareMethodListener`, AssertJ DSL for API responses.
- `framework-reporting` — Allure TestNG listener + `AllureHttpFilter` for HTTP step logging (request/response attachments) + vendored `AllureAspectJ` for automatic assertion step generation.
- `framework-splunk` — Splunk REST client, SPL query builder, AssertJ assertion DSL (`SplunkResponseAssert`, `SplunkResultAssert`).
- `framework-bundle` — aggregation entry point; the single dependency consumers import.

## Test architecture

Tests follow a flat pattern: **Test → `call()` → HttpClient**, with an AssertJ DSL layer for API response assertions.

```java
public class PostmanEchoIntegrationTest extends BaseApiTest {

    @Override
    protected String domain() { return "postman-echo"; }

    @Description("Verifies that GET /get response conforms to the JSON schema and matches the golden-file snapshot")
    @Test(description = "GET /get should match schema and snapshot contract")
    public void shouldMatchEchoGetContractAndSnapshot() {
        var response = call("get-echo", String.class)
                .query("suite", "integration")
                .send();

        ApiResponseAssert.assertThat(response)
                .hasStatus(200)
                .body()
                .field("args.suite").hasValue("integration")
                .matchesSchema("schemas/postman-echo-get.schema.json")
                .matchesSnapshot("postman-echo-get");
    }
}
```

### AssertJ DSL

`framework-test-support` provides a generic fluent `ApiResponse<T>` assertion DSL built on Jackson `JsonNode` navigation:

| Class | Role |
|---|---|
| `ApiResponseAssert<T>` | Entry point. `assertThat(response)` → fluent chain. Methods: `hasStatus(int)`, `body()`. |
| `AbstractApiResponseAssert` | Base class. Parses `rawBody` once (lazy-cached `JsonNode`). Produces `BodyAssert` via `body()`. |
| `BodyAssert` | Body-level assertions. `isNotEmpty()`, `at(int)`, `first()`, `field(String dotPath)`, `hasField(String dotPath)`, `matchesSchema(String)`, `matchesSnapshot(String)`. |
| `FieldAssert` | Field-level terminal assertions. `hasValue(Object)`, `isNotBlank()`, `isNotEmpty()`, `isPresent()`. All terminals return `BodyAssert` for continued chaining. |

Chain return types: `.body()` → `BodyAssert` → `.field("x")` → `FieldAssert` → `.hasValue(...)` → `BodyAssert` → `.matchesSchema(...)` → `BodyAssert`.

Schema and snapshot validation always operate on the **full original response JSON** (`rawBody`), regardless of how deep `.first()` or `.at(i)` has navigated the `JsonNode` cursor. `.matchesSchema()` / `.matchesSnapshot()` are only on `BodyAssert` — calling them on `FieldAssert` is a compile error by design.

## Running tests

```bash
# All tests
./gradlew test

# Suite-specific submodules not yet added to build

# With retry (env vars, not system properties)
FRAMEWORK_RETRY_MAX_RETRIES=2 FRAMEWORK_RETRY_DELAY_MS=500 ./gradlew test
```


## Allure reports

Allure Gradle plugin not yet configured. Results written to `build/allure-results`. Use Allure CLI directly:

```bash
# Serve report from results
allure serve build/allure-results

# Generate static report
allure generate build/allure-results -o build/allure-report
```
