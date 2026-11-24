package com.teamup.teamup.controller

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException::class)
    fun requestFailure(error: ResponseStatusException): ResponseEntity<ProblemDetail> = ResponseEntity
        .status(error.statusCode).headers(error.headers).body(error.body)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "The data changed or this request was already processed. Reload and retry.",
    )
}
