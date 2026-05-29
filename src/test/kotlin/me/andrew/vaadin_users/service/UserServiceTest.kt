
package me.andrew.vaadin_users.service

import me.andrew.vaadin_users.domain.AppUser
import me.andrew.vaadin_users.domain.Role
import me.andrew.vaadin_users.repos.AppUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional

class UserServiceTest {

    private val repository: AppUserRepository = mock()
    private val passwordEncoder = BCryptPasswordEncoder()
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userService = UserService(repository, passwordEncoder)
    }

    private fun loginAs(username: String, role: Role) {
        val authority = SimpleGrantedAuthority("ROLE_${role.name}")
        val auth = UsernamePasswordAuthenticationToken(username, null, listOf(authority))
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun makeUser(
        id: Long = 1L,
        username: String = "john",
        email: String = "john@example.com",
        role: Role = Role.USER
    ) = AppUser(
        id = id,
        username = username,
        email = email,
        passwordHash = requireNotNull(passwordEncoder.encode("password123")),
        role = role
    )

    @Test
    fun `findUsers - empty query returns all users`() {
        val users = listOf(makeUser(1), makeUser(2, username = "jane", email = "jane@example.com"))
        whenever(repository.findAll(any<Pageable>()))
            .thenReturn(PageImpl(users))

        val result = userService.findUsers(
            query = "",
            searchType = UserSearchType.USERNAME,
            page = 0,
            size = 25,
            sortProperty = "username",
            sortDirection = org.springframework.data.domain.Sort.Direction.ASC
        )

        assertEquals(2, result.totalElements)
        verify(repository).findAll(any<Pageable>())
    }

    @Test
    fun `findUsers - username search calls searchByUsername`() {
        whenever(repository.searchByUsername(any(), any()))
            .thenReturn(PageImpl(listOf(makeUser())))

        userService.findUsers(
            query = "john",
            searchType = UserSearchType.USERNAME,
            page = 0,
            size = 25,
            sortProperty = "username",
            sortDirection = org.springframework.data.domain.Sort.Direction.ASC
        )

        verify(repository).searchByUsername(any(), any())
        verify(repository, never()).searchByEmail(any(), any())
    }

    @Test
    fun `findUsers - email search calls searchByEmail`() {
        whenever(repository.searchByEmail(any(), any()))
            .thenReturn(PageImpl(listOf(makeUser())))

        userService.findUsers(
            query = "john@example.com",
            searchType = UserSearchType.EMAIL,
            page = 0,
            size = 25,
            sortProperty = "email",
            sortDirection = org.springframework.data.domain.Sort.Direction.ASC
        )

        verify(repository).searchByEmail(any(), any())
        verify(repository, never()).searchByUsername(any(), any())
    }

    @Test
    fun `findUsers - page size is capped at 100`() {
        whenever(repository.findAll(any<Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        userService.findUsers(
            query = "",
            searchType = UserSearchType.USERNAME,
            page = 0,
            size = 9999,
            sortProperty = "username",
            sortDirection = org.springframework.data.domain.Sort.Direction.ASC
        )

        verify(repository).findAll(argThat<Pageable> { pageSize == 100 })
    }

    @Test
    fun `createUser - admin can create a user`() {
        loginAs("admin", Role.ADMIN)

        whenever(repository.existsByUsername("newuser")).thenReturn(false)
        whenever(repository.existsByEmail("new@example.com")).thenReturn(false)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val result = userService.createUser(
            username = "newuser",
            email = "new@example.com",
            rawPassword = "password123",
            role = Role.USER
        )

        assertNotNull(result)
        assertEquals("newuser", result.username)
        assertEquals("new@example.com", result.email)
        verify(repository).save(any())
    }

    @Test
    fun `createUser - non-admin throws AccessDeniedException`() {
        loginAs("user", Role.USER)

        assertThrows<AccessDeniedException> {
            userService.createUser(
                username = "newuser",
                email = "new@example.com",
                rawPassword = "password123",
                role = Role.USER
            )
        }

        verify(repository, never()).save(any())
    }

    @Test
    fun `createUser - duplicate username throws exception`() {
        loginAs("admin", Role.ADMIN)

        whenever(repository.existsByUsername("john")).thenReturn(true)

        val ex = assertThrows<IllegalArgumentException> {
            userService.createUser(
                username = "john",
                email = "john@example.com",
                rawPassword = "password123",
                role = Role.USER
            )
        }

        assertEquals("Username already exists", ex.message)
        verify(repository, never()).save(any())
    }

    @Test
    fun `createUser - duplicate email throws exception`() {
        loginAs("admin", Role.ADMIN)

        whenever(repository.existsByUsername("newuser")).thenReturn(false)
        whenever(repository.existsByEmail("john@example.com")).thenReturn(true)

        val ex = assertThrows<IllegalArgumentException> {
            userService.createUser(
                username = "newuser",
                email = "john@example.com",
                rawPassword = "password123",
                role = Role.USER
            )
        }

        assertEquals("Email already exists", ex.message)
    }

    @Test
    fun `createUser - blank password throws exception`() {
        loginAs("admin", Role.ADMIN)

        whenever(repository.existsByUsername("newuser")).thenReturn(false)
        whenever(repository.existsByEmail("new@example.com")).thenReturn(false)

        val ex = assertThrows<IllegalArgumentException> {
            userService.createUser(
                username = "newuser",
                email = "new@example.com",
                rawPassword = "",
                role = Role.USER
            )
        }

        assertEquals("Password is required", ex.message)
    }

    @Test
    fun `createUser - invalid email throws exception`() {
        loginAs("admin", Role.ADMIN)

        whenever(repository.existsByUsername("newuser")).thenReturn(false)

        val ex = assertThrows<IllegalArgumentException> {
            userService.createUser(
                username = "newuser",
                email = "notanemail",
                rawPassword = "password123",
                role = Role.USER
            )
        }

        assertEquals("Email is invalid (e.g. user@example.com)", ex.message)
    }

    @Test
    fun `createUser - username shorter than 3 characters throws exception`() {
        loginAs("admin", Role.ADMIN)

        val ex = assertThrows<IllegalArgumentException> {
            userService.createUser(
                username = "ab",
                email = "ab@example.com",
                rawPassword = "password123",
                role = Role.USER
            )
        }

        assertEquals("Username must be at least 3 characters", ex.message)
    }

    @Test
    fun `updateUser - admin can update a user`() {
        loginAs("admin", Role.ADMIN)

        val existing = makeUser(id = 1L, username = "john", email = "john@example.com")
        whenever(repository.findById(1L)).thenReturn(Optional.of(existing))
        whenever(repository.findByUsername("newname")).thenReturn(null)
        whenever(repository.findByEmail("new@example.com")).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val result = userService.updateUser(
            id = 1L,
            username = "newname",
            email = "new@example.com",
            rawPassword = null,
            role = Role.ADMIN
        )

        assertEquals("newname", result.username)
        assertEquals("new@example.com", result.email)
        assertEquals(Role.ADMIN, result.role)
    }

    @Test
    fun `updateUser - blank password does not change existing hash`() {
        loginAs("admin", Role.ADMIN)

        val originalHash = requireNotNull(passwordEncoder.encode("oldpassword"))
        val existing = makeUser(id = 1L).apply { passwordHash = originalHash }

        whenever(repository.findById(1L)).thenReturn(Optional.of(existing))
        whenever(repository.findByUsername(any())).thenReturn(null)
        whenever(repository.findByEmail(any())).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val result = userService.updateUser(
            id = 1L,
            username = "john",
            email = "john@example.com",
            rawPassword = "",
            role = Role.USER
        )

        assertEquals(originalHash, result.passwordHash)
    }

    @Test
    fun `updateUser - user not found throws exception`() {
        loginAs("admin", Role.ADMIN)

        whenever(repository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<IllegalArgumentException> {
            userService.updateUser(
                id = 999L,
                username = "someone",
                email = "someone@example.com",
                rawPassword = null,
                role = Role.USER
            )
        }

        assertEquals("User not found", ex.message)
    }

    @Test
    fun `deleteUser - admin can delete another user`() {
        loginAs("admin", Role.ADMIN)

        val user = makeUser(id = 1L, username = "john")
        whenever(repository.findById(1L)).thenReturn(Optional.of(user))

        userService.deleteUser(1L)

        verify(repository).delete(user)
    }

    @Test
    fun `deleteUser - admin cannot delete own account`() {
        loginAs("admin", Role.ADMIN)

        val adminUser = makeUser(id = 1L, username = "admin", role = Role.ADMIN)
        whenever(repository.findById(1L)).thenReturn(Optional.of(adminUser))

        val ex = assertThrows<IllegalArgumentException> {
            userService.deleteUser(1L)
        }

        assertEquals("You cannot delete your own account", ex.message)
        verify(repository, never()).delete(any())
    }

    @Test
    fun `deleteUser - non-admin throws AccessDeniedException`() {
        loginAs("user", Role.USER)

        assertThrows<AccessDeniedException> {
            userService.deleteUser(1L)
        }

        verify(repository, never()).delete(any())
    }

    @Test
    fun `deleteUser - user not found throws exception`() {
        loginAs("admin", Role.ADMIN)

        whenever(repository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<IllegalArgumentException> {
            userService.deleteUser(999L)
        }

        assertEquals("User not found", ex.message)
    }
}