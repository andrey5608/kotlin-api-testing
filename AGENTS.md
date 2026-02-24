# AGENTS.md — API test automation (Maven)

## Stack
- Kotlin (JVM), Maven
- JUnit 5 (Jupiter) executed by Maven Surefire
- CI: GitHub Actions
- Postman collections in: /postman/

## Local commands (preferred)
- mvn -q -DskipTests=false test
- mvn -q -Dtest=*Smoke* test
- mvn -q -Dgroups=smoke test   (only if groups/tags are configured)
- mvn -q test

## Test execution notes
- JUnit 5 runs via Maven Surefire with JUnit Platform. If you change test execution behavior (parallel, tags),
  do it in pom.xml under maven-surefire-plugin configurationParameters. [example: junit.jupiter.execution.parallel.enabled=true]
- Keep tests deterministic: no sleeps, no reliance on local timezone/clock, stable assertions.

## Configuration / secrets
- Never commit secrets (ORG_ADMIN_API_KEY, CUSTOMER_CODE, BASE_URL).
- Read config from env vars or Maven properties:
  - BASE_URL (required)
  - ORG_ADMIN_API_KEY (required)
  - CUSTOMER_CODE (optional if endpoint needs it)
- In CI, use GitHub Actions secrets and pass to Maven as env vars.

## What to deliver in PRs
- Tests with clear naming and Arrange/Act/Assert structure.
- Update docs/qa/README.md if you add new env vars, profiles, or run commands.
- If you touch GitHub Actions workflow, keep it minimal and make sure it uploads test reports as artifacts.

## Safety rules
- Do not add destructive endpoints (DELETE/revoke/rotate token) to default PR test run.
  Put them behind a Maven profile (e.g., -Pdestructive) or explicit env flag (RUN_DESTRUCTIVE=true).

## Postman collection usage (if relevant)
- Collection: postman/JetBrains-Account-API.postman_collection.json
- If you create/modify collections, keep them environment-agnostic (use {{baseUrl}} etc.) and store secrets only in local env templates (not committed).
