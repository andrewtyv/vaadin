package me.andrew.vaadin_users.repos

import me.andrew.vaadin_users.domain.AppUser
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AppUserRepository: JpaRepository<AppUser, Long> {

    fun findByUsername(username: String): AppUser?

    fun existsByUsername(username: String): Boolean

    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): AppUser?

    @Query(
        """
        select u from AppUser u
        where lower(u.username) like lower(concat('%', :username, '%'))
        """
    )
    fun searchByUsername(
        @Param("username") username: String,
        pageable: Pageable
    ): Page<AppUser>

    @Query(
        """
        select u from AppUser u
        where lower(u.email) like lower(concat('%', :email, '%'))
        """
    )
    fun searchByEmail(
        @Param("email") email: String,
        pageable: Pageable
    ): Page<AppUser>
}