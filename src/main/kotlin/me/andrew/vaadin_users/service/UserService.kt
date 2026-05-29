package me.andrew.vaadin_users.service

import me.andrew.vaadin_users.domain.AppUser
import me.andrew.vaadin_users.domain.Role
import me.andrew.vaadin_users.repos.AppUserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.AccessDeniedException

enum class UserSearchType {
    USERNAME,
    EMAIL
}


@Service
class UserService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private val allowedSortProperties = setOf(
        "username",
        "email",
        "createdAt",
        "updatedAt"
    )
    @Transactional(readOnly = true)
    fun findUsers(
        query: String,
        searchType: UserSearchType,
        page: Int,
        size: Int,
        sortProperty: String,
        sortDirection: Sort.Direction
    ): Page<AppUser> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(10, 100)

        val safeSortProperty = if (allowedSortProperties.contains(sortProperty)) {
            sortProperty
        } else {
            "username"
        }

        val pageable = PageRequest.of(
            safePage,
            safeSize,
            Sort.by(sortDirection, safeSortProperty)
        )

        val normalizedQuery = query.trim()

        if (normalizedQuery.isBlank()) {
            return appUserRepository.findAll(pageable)
        }

        return when (searchType) {
            UserSearchType.USERNAME -> {
                appUserRepository.searchByUsername(normalizedQuery, pageable)
            }

            UserSearchType.EMAIL -> {
                appUserRepository.searchByEmail(normalizedQuery.lowercase(), pageable)
            }
        }
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    fun createUser(
        username: String,
        email: String,
        rawPassword: String,
        role: Role
    ): AppUser {
        requireAdmin(); // very bad design decision to make unit tests work


        val cleanUsername = username.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPassword = rawPassword.trim()

        validateUsername(cleanUsername)
        validateEmail(cleanEmail)

        if (cleanPassword.isBlank()) {
            throw IllegalArgumentException("Password is required")
        }

        if (appUserRepository.existsByUsername(cleanUsername)) {
            throw IllegalArgumentException("Username already exists")
        }

        if (appUserRepository.existsByEmail(cleanEmail)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user = AppUser(
            username = cleanUsername,
            email = cleanEmail,
            passwordHash = encodePassword(cleanPassword),
            role = role
        )

        return appUserRepository.save(user)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    fun updateUser(
        id: Long,
        username: String,
        email: String,
        rawPassword: String?,
        role: Role
    ): AppUser {
        requireAdmin(); // very bad design decision to make unit tests work

        val user = appUserRepository.findById(id)
            .orElseThrow { IllegalArgumentException("User not found") }

        val cleanUsername = username.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPassword = rawPassword?.trim().orEmpty()

        validateUsername(cleanUsername)
        validateEmail(cleanEmail)

        val userWithSameUsername = appUserRepository.findByUsername(cleanUsername)
        if (userWithSameUsername != null && userWithSameUsername.id != user.id) {
            throw IllegalArgumentException("Username already exists")
        }

        val userWithSameEmail = appUserRepository.findByEmail(cleanEmail)
        if (userWithSameEmail != null && userWithSameEmail.id != user.id) {
            throw IllegalArgumentException("Email already exists")
        }

        user.username = cleanUsername
        user.email = cleanEmail
        user.role = role

        if (cleanPassword.isNotBlank()) {
            user.passwordHash = encodePassword(cleanPassword)
        }

        return appUserRepository.save(user)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    fun deleteUser(id: Long) {
        requireAdmin(); // very bad design decision to make unit tests work
        val user = appUserRepository.findById(id)
            .orElseThrow { IllegalArgumentException("User not found") }

        val currentUsername = SecurityContextHolder.getContext().authentication?.name

        if (user.username == currentUsername) {
            throw IllegalArgumentException("You cannot delete your own account")
        }

        appUserRepository.delete(user)
    }

    private fun validateUsername(username: String) {
        if (username.isBlank()) {
            throw IllegalArgumentException("Username is required")
        }

        if (username.length < 3) {
            throw IllegalArgumentException("Username must be at least 3 characters")
        }
    }

    private fun validateEmail(email: String) {
        if (email.isBlank()) {
            throw IllegalArgumentException("Email is required")
        }

        val emailRegex = Regex("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")
        if (!emailRegex.matches(email)) {
            throw IllegalArgumentException("Email is invalid (e.g. user@example.com)")
        }
    }

    private fun encodePassword(rawPassword: String): String {
        return passwordEncoder.encode(rawPassword)
            ?: throw IllegalStateException("Password encoder returned null")
    }

    private fun requireAdmin() {
        val authentication = SecurityContextHolder.getContext().authentication

        val isAdmin = authentication?.authorities
            ?.any { it.authority == "ROLE_ADMIN" } == true

        if (!isAdmin) {
            throw AccessDeniedException("Admin access required")
        }
    }
}