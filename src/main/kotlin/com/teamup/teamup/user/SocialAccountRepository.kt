package com.teamup.teamup.user

import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderUserId(
        provider: AuthProvider,
        providerUserId: String,
    ): SocialAccount?
}
