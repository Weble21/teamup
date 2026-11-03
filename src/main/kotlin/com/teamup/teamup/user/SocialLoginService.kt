package com.teamup.teamup.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SocialLoginService(
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
) {
    // Call only after the server has authenticated the social provider response.
    @Transactional
    fun findOrCreateUser(
        provider: AuthProvider,
        providerUserId: String,
        name: String,
        email: String?,
    ): User {
        require(providerUserId.isNotBlank() && providerUserId.length <= 255) {
            "Invalid social provider user ID"
        }

        val account = socialAccountRepository.findByProviderAndProviderUserId(
            provider,
            providerUserId,
        )
        if (account != null) {
            return account.user
        }

        val displayName = name.trim()
        require(displayName.isNotEmpty() && displayName.length <= 100) {
            "User name must contain 1 to 100 characters"
        }
        require(email == null || email.length <= 255) { "Email is too long" }

        val user = userRepository.save(User(name = displayName, email = email))
        socialAccountRepository.save(
            SocialAccount(
                user = user,
                provider = provider,
                providerUserId = providerUserId,
            ),
        )
        return user
    }
}
