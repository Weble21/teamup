package com.teamup.teamup.controller

import com.teamup.teamup.view.CsrfResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController {
    @GetMapping("/csrf")
    fun csrf(token: CsrfToken): CsrfResponse = CsrfResponse(token.headerName, token.token)
}
