package com.teamup.teamup.model.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ClassifiedClause(val type: String, val score: Double)
data class ClassificationResponse(val results: List<ClassifiedClause>)

@Component
class EmbeddingConstraintClient(
    private val mapper: ObjectMapper,
    @Value("\${CONSTRAINTS_URL:http://localhost:8001}") private val baseUrl: String,
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    fun classify(clauses: List<String>): List<ClassifiedClause> {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/classify"))
            .timeout(Duration.ofSeconds(60))
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mapOf("clauses" to clauses))))
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Condition interpreter interrupted", e)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Condition interpreter unavailable", e)
        }
        if (response.statusCode() != 200) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Condition interpreter unavailable")
        val result = try { mapper.readValue(response.body(), ClassificationResponse::class.java).results }
            catch (e: Exception) { throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid condition interpreter response", e) }
        if (result.size != clauses.size) throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid condition interpreter response")
        return result
    }
}
