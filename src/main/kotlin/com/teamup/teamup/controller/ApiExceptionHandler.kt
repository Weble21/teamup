package com.teamup.teamup.controller

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "The data changed or this request was already processed. Reload and retry.",
    )
}
