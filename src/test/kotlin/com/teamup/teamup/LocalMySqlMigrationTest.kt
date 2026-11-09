package com.teamup.teamup

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Explicit opt-in only: applies migrations to the configured local DB; never deletes data.
@EnabledIfSystemProperty(named = "teamup.verifyMysql", matches = "true")
class LocalMySqlMigrationTest {
    @Test
    fun `configured MySQL accepts connection and current migrations`() {
        val properties = Properties().apply { Files.newBufferedReader(Path.of(".env")).use { load(it) } }
        fun setting(key: String): String = System.getenv(key) ?: properties.getProperty(key)
            ?: error("Missing $key")
        val url = setting("DB_URL")
        require(url.startsWith("jdbc:mysql://localhost:") || url.startsWith("jdbc:mysql://127.0.0.1:")) {
            "This check is restricted to local MySQL"
        }
        val username = setting("DB_USERNAME"); val password = setting("DB_PASSWORD")
        val flyway = Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").load()
        flyway.migrate()
        assertTrue(flyway.validateWithResult().validationSuccessful)
        DriverManager.getConnection(url, username, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '5'").use { result ->
                    result.next(); assertEquals(1, result.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM matches").use { result -> assertTrue(result.next()) }
            }
        }
    }
}
