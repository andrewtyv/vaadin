package me.andrew.vaadin_users.service

import me.andrew.vaadin_users.domain.AppUser
import me.andrew.vaadin_users.domain.Role
import me.andrew.vaadin_users.repos.AppUserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class IniSeed(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (appUserRepository.count() > 0) {
            return
        }

        val users = mutableListOf<AppUser>()

        users += AppUser(
            username = "admin",
            email = "admin@example.com",
            passwordHash = encodePassword("admin123"),
            role = Role.ADMIN
        )

        users += AppUser(
            username = "user",
            email = "user@example.com",
            passwordHash = encodePassword("user123"),
            role = Role.USER
        )

        val defaultPasswordHash = encodePassword("user123")

        for (i in 1..498) {
            users += AppUser(
                username = "test_user_%03d".format(i),
                email = "test%03d@example.com".format(i),
                passwordHash = defaultPasswordHash,
                role = Role.USER
            )
        }

        appUserRepository.saveAll(users)
    }
    private fun encodePassword(rawPassword: String): String {
        return passwordEncoder.encode(rawPassword)
            ?: throw IllegalStateException("Password encoder returned null")
    }
}