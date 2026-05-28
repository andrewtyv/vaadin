package me.andrew.vaadin_users.service

import me.andrew.vaadin_users.repos.AppUserRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class DatabaseUserDetailsService(
    private val appUserRepository: AppUserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val appUser = appUserRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found: $username")

        return User.withUsername(appUser.username)
            .password(appUser.passwordHash)
            .roles(appUser.role.name)
            .build()
    }
}