package com.teamup.teamup.model.auth

import com.teamup.teamup.model.service.SocialLoginService
import org.springframework.context.annotation.Profile
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
@Profile("oauth")
class SocialOAuth2UserService(
    private val socialLoginService: SocialLoginService,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(request: OAuth2UserRequest): OAuth2User {
        val providerUser = delegate.loadUser(request)
        val profile = SocialProfileMapper.fromOAuth2(
            request.clientRegistration.registrationId,
            providerUser.attributes,
        )
        val user = socialLoginService.findOrCreateUser(
            profile.provider, profile.providerUserId, profile.name, profile.email,
        )
        return DefaultOAuth2User(
            listOf(SimpleGrantedAuthority("ROLE_USER")),
            mapOf("userId" to checkNotNull(user.id).toString()),
            "userId",
        )
    }
}

@Service
@Profile("oauth")
class SocialOidcUserService(
    private val socialLoginService: SocialLoginService,
) : OAuth2UserService<OidcUserRequest, OidcUser> {
    private val delegate = OidcUserService()

    override fun loadUser(request: OidcUserRequest): OidcUser {
        if (request.clientRegistration.registrationId != "google") {
            throw OAuth2AuthenticationException(OAuth2Error("unsupported_provider"))
        }
        val providerUser = delegate.loadUser(request)
        val profile = SocialProfileMapper.fromGoogle(providerUser.claims)
        val user = socialLoginService.findOrCreateUser(
            profile.provider, profile.providerUserId, profile.name, profile.email,
        )
        return AppOidcUser(providerUser, checkNotNull(user.id))
    }
}

// Preserve verified OIDC claims while using our DB user ID as the session identity.
class AppOidcUser(
    private val delegate: OidcUser,
    val userId: Long,
) : OidcUser by delegate {
    override fun getName(): String = userId.toString()

    override fun getAuthorities(): Collection<GrantedAuthority> =
        delegate.authorities + SimpleGrantedAuthority("ROLE_USER")
}
