package com.api.testing.config

import org.yaml.snakeyaml.Yaml

/**
 * Test configuration loaded from YAML files on the test classpath.
 *
 * Loading order (later entries override earlier ones):
 *   1. `src/test/resources/config.yml`       — committed base config (no secrets)
 *   2. `src/test/resources/config.local.yml` — optional local override (git-ignored)
 *
 * The only secret is [apiKey], which must be supplied via the `API_KEY` environment variable
 * (or `-DAPI_KEY=…` Maven property). Everything else lives in config.yml.
 */
object TestConfig {

    private val props: Map<String, Any> = loadConfig()

    val baseUrl: String     = get("baseUrl")
    val apiKey: String      = requireSecret("API_KEY")
    val customerCode: String = get("customerCode")
    val sourceTeamId: Int   = get("sourceTeamId").toInt()
    val targetTeamId: Int   = get("targetTeamId").toInt()
    val testUserEmail: String = get("testUserEmail")

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
