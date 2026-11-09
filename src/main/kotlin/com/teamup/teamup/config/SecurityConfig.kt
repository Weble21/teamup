package com.teamup.teamup.config

import com.teamup.teamup.model.auth.SocialOAuth2UserService
import com.teamup.teamup.model.auth.SocialOidcUserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
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
            .exceptionHandling { exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                )
            }
            .logout { logout ->
                logout.logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler { _, response, _ ->
                        response.status = HttpStatus.NO_CONTENT.value()
                    }
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
