# lx-test-kit

Multi-module REST API test framework. Java 21 · Gradle 9.4 · TestNG · REST Assured · Allure.

## Prerequisites

- Java 21
- Gradle wrapper included (`./gradlew`)
- [Allure CLI](https://allurereport.org/docs/install/) for local reports
- GitHub Packages read access (`GITHUB_ACTOR` / `GITHUB_TOKEN`)

## Quick start

Add the repository and single bundle dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/ftoropov-leadex/lx-test-kit")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    testImplementation("systems.leadex.lxtestkit:framework-bundle:1.0.0")
}
```

`framework-bundle` re-exports all modules via `api()` — no other framework dependencies needed.

## Modules

```
framework-bundle  (umbrella, re-exports everything)
├── api ──► framework-core          (leaf — zero internal deps)
├── api ──► framework-test-support  ──► api: framework-core
├── api ──► framework-reporting     ──► api: framework-core
│                                   └── impl: framework-test-support
└── api ──► framework-splunk        ──► api: framework-core
```

| Module | Responsibility |
|--------|---------------|
| `framework-core` | `HttpClient` interface, `RestAssuredHttpClient`, `CorrelationIdFilter`, config, `ApiResponse<T>` |
| `framework-test-support` | `BaseApiTest`, `ApiRequestBuilder`, AssertJ DSL (`ApiResponseAssert`, `BodyAssert`, `FieldAssert`), schema/snapshot validators, retry, network detection |
| `framework-reporting` | Allure TestNG listener, `AllureHttpFilter` (request/response attachments), `AllureAspectJ` LTW for automatic assertion steps |
| `framework-splunk` | `SplunkClient`, `SplunkQueryBuilder`, `SplunkResponseAssert` / `SplunkResultAssert` DSL |
| `framework-bundle` | Umbrella POM — no source |

## Test architecture

Tests extend `BaseApiTest`, override `domain()`, and call endpoints by key:

```java
public class PostmanEchoIntegrationTest extends BaseApiTest {

    @Override
    protected String domain() { return "postman-echo"; }

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

Call flow: `call()` → `ApiRequestBuilder` → `UrlResolver` → `RestAssuredHttpClient` → `CorrelationIdFilter`.
Allure HTTP steps are attached automatically by `AllureHttpFilter`. Do **not** add `Allure.step()` manually — `AllureAspectJ` LTW generates assertion steps automatically.

`.query()`, `.pathParam()`, `.bodyField()` silently skip `null` values — no null-guard needed.

### AssertJ DSL

Entry point: `ApiResponseAssert.assertThat(response)`.

| Class | Role |
|-------|------|
| `ApiResponseAssert<T>` | Entry point. `hasStatus(int)` → `body()`. |
| `AbstractApiResponseAssert` | Parses `rawBody` once (lazy-cached `JsonNode`). |
| `BodyAssert` | `isNotEmpty()`, `at(int)`, `first()`, `field(dotPath)`, `hasField(dotPath)`, `matchesSchema(path)`, `matchesSnapshot(name)`. |
| `FieldAssert` | Terminal assertions: `hasValue(Object)`, `isNotBlank()`, `isNotEmpty()`, `isPresent()`. All return `BodyAssert` for chaining. |

Chain: `.body()` → `BodyAssert` → `.field("x")` → `FieldAssert` → `.hasValue(...)` → `BodyAssert` → `.matchesSchema(...)` → `BodyAssert`.

`.matchesSchema()` and `.matchesSnapshot()` always validate the **full original `rawBody`**, regardless of cursor depth. Both are only on `BodyAssert` — calling them on `FieldAssert` is a compile error by design.

## Environment variables

| Variable | Required | Default | Used by |
|----------|----------|---------|---------|
| `FRAMEWORK_BASE_URL` | yes | — | `ConfigResolver` |
| `FRAMEWORK_DOMAINS_PATH` | yes | — | `BaseApiTest` |
| `FRAMEWORK_ENV` | no | `dev` | `ConfigResolver` |
| `FRAMEWORK_CONNECT_TIMEOUT` | no | `5000` | `ConfigResolver` |
| `FRAMEWORK_READ_TIMEOUT` | no | `15000` | `ConfigResolver` |
| `FRAMEWORK_CLIENT_NAME` | no | `""` | `BaseApiTest` (Basic Auth) |
| `FRAMEWORK_CLIENT_SECRET` | no | `""` | `BaseApiTest` (Basic Auth) |
| `SPLUNK_BASE_URL` | yes* | — | `SplunkConnectionConfig` |
| `GITHUB_ACTOR` / `GITHUB_TOKEN` | CI only | — | GitHub Packages publish/resolve |

> \* `SPLUNK_BASE_URL` is resolved at class-load time. Set it even if Splunk assertions are not the focus of the current suite, if any class referencing `SplunkSupport` is on the classpath.

## Running tests

```bash
# All tests
./gradlew test

# With retry
FRAMEWORK_RETRY_MAX_RETRIES=2 FRAMEWORK_RETRY_DELAY_MS=500 ./gradlew test
```

## Allure reports

Results are written to `build/allure-results`. Use Allure CLI:

```bash
# Serve live report
allure serve build/allure-results

# Generate static report
allure generate build/allure-results -o build/allure-report
```
