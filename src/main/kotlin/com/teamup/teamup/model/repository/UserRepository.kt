package com.teamup.teamup.model.repository

import com.teamup.teamup.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>
