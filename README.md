# kotlin-api-testing

API integration test framework for the [Account API](https://account.jetbrains.com/api/v1).

## Goal

Validate key customer-facing endpoints — license assignment, license team transfer, and token
authentication — against the real Account API using automated integration tests.

## Stack

| | |
|---|---|
| Language | Kotlin 2.1.20 |
| Test framework | JUnit 5.10.3 |
| HTTP client | Apache HttpClient 5.3.1 |
| Assertions | AssertJ 3.26.3 |
| Build | Maven 3.8+ |
| Reporting | Allure 2.x (Homebrew) |
| CI | GitHub Actions |

## Get Started

### Prerequisites

- JDK 17+
- Maven 3.8+
- [Allure CLI](https://allurereport.org/docs/install/) — `brew install allure`
- A valid Account API key

### 1. Configure

Edit `src/test/resources/config.yml` with your organisation's values:

```yaml
baseUrl: https://account.jetbrains.com/api/v1
customerCode: <your customer code>
sourceTeamId: <team with unassigned licenses>
targetTeamId: <different team for transfer tests>
testUserEmail: <org member email for assign tests>
```

For local overrides without touching committed files:

```bash
cp src/test/resources/config.local.yml.example src/test/resources/config.local.yml
# edit config.local.yml — it is git-ignored
```

### 2. Set the API key

```bash
export API_KEY=your_api_key
```

### 3. Run tests

```bash
mvn clean test
```

### 4. View the report

```bash
allure serve target/allure-results
```

## Framework Structure

```
src/
  main/kotlin/com/api/testing/
    client/         # ApiClient — typed wrappers around every endpoint
    config/         # TestConfig — reads config.yml / config.local.yml
    models/         # Request and response data classes

  test/kotlin/com/api/testing/
    base/           # BaseApiTest — shared @BeforeAll auth check, helpers
    extensions/     # LicenseCleanupExtension — reverts assigned licenses after each test
    smoke/          # TokenSmokeTest
    license/assign/ # AssignLicensePositiveTest, AssignLicenseNegativeTest
    license/team/   # ChangeLicensesTeamTest
    lifecycle/      # LifecycleTest (revoke / rotate — excluded from default run)

  test/resources/
    config.yml              # committed config (no secrets)
    config.local.yml.example
    allure.properties       # points Allure to target/allure-results
```

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `API_KEY` | **yes** | Account API key — never commit |

All other configuration is in `config.yml`.

## Running Specific Suites

```bash
mvn test -Dgroups=smoke
mvn test -Dgroups=positive
mvn test -Dgroups=negative
mvn test -Dtest=TokenSmokeTest
```

Lifecycle tests (permanently alter state — token rotate, license revoke) are excluded by default:

```bash
mvn test -Plifecycle   # run only with intent
```

## CI

GitHub Actions workflow: [`.github/workflows/api-tests.yml`](.github/workflows/api-tests.yml)

Requires one repository secret: **`API_KEY`**.

An Allure HTML report is generated via `mvn allure:report` and uploaded as the `allure-report` artifact on every run (retained 14 days).

## Known Issues

| Issue | Cause | Workaround |
|---|---|---|
| Tests skipped with *"token type TEAM"* | API key is team-scoped; assign and transfer endpoints require a customer-scoped key | Use a customer-level API key |
| `TOKEN_TYPE_MISMATCH 403` on assign / changeLicensesTeam | Same as above | Same as above |
| Lifecycle profile rotates the token | `LF-02` invalidates the current key | Update `API_KEY` in env and GitHub Secrets after running `-Plifecycle` |
| `targetTeamId` / `testUserEmail` placeholders in config | Real values are org-specific and not committed | Fill in `config.yml` or `config.local.yml` before running positive tests |
