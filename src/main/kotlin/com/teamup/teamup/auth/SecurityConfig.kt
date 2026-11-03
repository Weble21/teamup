package com.teamup.teamup.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@Profile("oauth")
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        oauth2UserService: SocialOAuth2UserService,
        oidcUserService: SocialOidcUserService,
    ): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests.requestMatchers("/login", "/login/**", "/oauth2/**", "/error").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { login ->
                login.userInfoEndpoint { endpoint ->
                    endpoint.userService(oauth2UserService)
                        .oidcUserService(oidcUserService)
                }
                login.successHandler { _, response, _ ->
                    response.contentType = "application/json"
                    response.characterEncoding = "UTF-8"
                    response.writer.write("{\"authenticated\":true}")
                }
            }
        return http.build()
    }
}
