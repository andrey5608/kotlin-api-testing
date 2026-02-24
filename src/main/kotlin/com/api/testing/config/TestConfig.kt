package com.api.testing.config

import org.yaml.snakeyaml.Yaml

/**
 * Test configuration loaded from YAML files on the test classpath.
 *
 * Loading order (later entries override earlier ones):
 *   1. `src/test/resources/config.yml`       — committed base config (no secrets)
 *   2. `src/test/resources/config.local.yml` — optional local override (git-ignored)
 *
 * The only secret is [apiKey], which must be supplied via the `ORG_ADMIN_API_KEY` environment variable
 * (or `-DORG_ADMIN_API_KEY=…` Maven property). Everything else lives in config.yml.
 */
object TestConfig {

    private val props: Map<String, Any> = loadConfig()

    val baseUrl: String        = get("baseUrl")
    val orgAdminKey: String    = requireSecret("ORG_ADMIN_API_KEY")
    val customerCode: String   = get("customerCode")
    val sourceTeamId: Int    = get("sourceTeamId").toInt()
    val targetTeamId: Int    = get("targetTeamId").toInt()
    val testUserEmail: String = get("testUserEmail")

    /**
     * Optional: a licenseId that belongs to a different team within the same org.
     * When set together with [teamAdminKey], used in AL-N16 to verify a team admin
     * cannot assign a license owned by a different team.
     * Leave blank or omit from config.yml to skip that test case.
     */
    val foreignLicenseId: String? = getOptional("foreignLicenseId")

    /**
     * Optional: API key scoped to a single team (team admin key for [sourceTeamId]).
     * Required for AL-N16: must NOT have access to the team that owns [foreignLicenseId].
     * Set via environment variable `TEAM_ADMIN_API_KEY`; never commit to config files.
     */
    val teamAdminKey: String? = getOptionalSecret("TEAM_ADMIN_API_KEY")

    // Loader

    @Suppress("UNCHECKED_CAST")
    private fun loadConfig(): Map<String, Any> {
        val yaml = Yaml()
        val loader = TestConfig::class.java.classLoader

        val base = loader.getResourceAsStream("config.yml")
            ?.let { yaml.load<Map<String, Any>>(it) }
            ?: error(
                "config.yml not found on the test classpath. " +
                    "Make sure src/test/resources/config.yml exists."
            )

        val local = loader.getResourceAsStream("config.local.yml")
            ?.let { yaml.load<Map<String, Any>>(it) }
            ?: emptyMap()

        return base + local
    }

    // Helpers

    private fun get(key: String): String =
        props[key]?.toString()
            ?: error(
                "Required config key '$key' not found in config.yml or config.local.yml."
            )

    private fun getOptional(key: String): String? =
        props[key]?.toString()?.takeIf { it.isNotBlank() }

    /**
     * Reads an optional secret from env vars or Maven `-D` properties.
     * Returns null if not set — callers should skip the test with `assumeTrue`.
     */
    private fun getOptionalSecret(envVar: String): String? =
        System.getenv(envVar)?.takeIf { it.isNotBlank() }
            ?: System.getProperty(envVar)?.takeIf { it.isNotBlank() }

    /**
     * Reads a secret value from environment variables or Maven `-D` system properties.
     * Secrets are never stored in config files.
     */
    private fun requireSecret(envVar: String): String =
        System.getenv(envVar)?.takeIf { it.isNotBlank() }
            ?: System.getProperty(envVar)?.takeIf { it.isNotBlank() }
            ?: error(
                "Required secret '$envVar' is missing. " +
                    "Set it as an environment variable or pass -D$envVar=<value> to Maven."
            )
}
