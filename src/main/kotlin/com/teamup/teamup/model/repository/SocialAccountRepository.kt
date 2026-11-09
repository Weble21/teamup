package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.AuthProvider
import com.teamup.teamup.model.entity.SocialAccount
import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderUserId(
        provider: AuthProvider,
        providerUserId: String,
    ): SocialAccount?
}
