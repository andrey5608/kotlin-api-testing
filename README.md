# kotlin-api-testing

Integration tests for the JetBrains Account API — license assignment, team transfer, and token auth.

## Stack

Kotlin 2.1.20 · JUnit 5 · Apache HttpClient 5 · AssertJ · Maven · Allure · GitHub Actions

## Setup

**Prerequisites:** JDK 21+, Maven 3.8+, `brew install allure`

**1. Edit `src/test/resources/config.yml`:**

```yaml
baseUrl: https://account.jetbrains.com/api/v1
customerCode: <your customer code>
sourceTeamId: <team with unassigned licenses>
targetTeamId: <different team for transfer tests>
testUserEmail: <org member email>
```

Use `config.local.yml` (git-ignored) for local overrides — copy from `config.local.yml.example`.

**2. Set the API key:**

```bash
export ORG_ADMIN_API_KEY=your_api_key
```

**3. Run:**

```bash
mvn test
allure serve target/allure-results
```

## Test Suites

```bash
mvn test -Dgroups=smoke
mvn test -Dgroups=positive
mvn test -Dgroups=negative
mvn test -Dtest=TokenSmokeTest
mvn test -Plifecycle          # destructive: token rotate, license revoke — run with intent
```

## Project Layout

```
src/main/kotlin/.../
  client/     ApiClient — typed HTTP wrappers
  config/     TestConfig — reads config.yml / config.local.yml
  models/     Request and response data classes

src/test/kotlin/.../
  base/           BaseApiTest — @BeforeAll auth check, shared helpers
  extensions/     LicenseCleanupExtension — revokes assigned licenses after each test
  smoke/          TokenSmokeTest
  license/assign/ AssignLicensePositiveTest, AssignLicenseNegativeTest
  license/team/   ChangeLicensesTeamTest
  lifecycle/      LifecycleTest (excluded from default run)
```

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `ORG_ADMIN_API_KEY` | yes | Org Admin Account API key — never commit |
| `TEAM_ADMIN_API_KEY` | no | Team-scoped key for AL-N16 (cross-team access test) |

## CI

GitHub Actions (`.github/workflows/api-tests.yml`). Requires `ORG_ADMIN_API_KEY` repository secret.

**Test report (Allure, updated on every run):**  
https://andrey5608.github.io/kotlin-api-testing/

Allure report is also uploaded as a build artifact on every run (retained 14 days).

## Common Issues

| Symptom | Fix |
|---|---|
| Tests skipped with *"token type TEAM"* | Use a customer-level (org-wide) API key |
| `TOKEN_TYPE_MISMATCH 403` on assign / changeLicensesTeam | Same |
| After `-Plifecycle`: auth broken | `POST /token/rotate` invalidates the current key — update `ORG_ADMIN_API_KEY` in env and GitHub Secrets |
