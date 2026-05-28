package me.andrew.vaadin_users.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant


@Entity
@Table(name ="users" )
open class AppUser(

    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    open var id: Long? = null,

    @field:Column(name = "username", nullable = false, length = 120)
    open var username: String = "",

    @field:Column(name = "email", nullable = false, unique = true, length = 255)
    open var email: String = "",

    @field:Column(name = "password_hash", nullable = false, length = 255)
    open var passwordHash: String = "",

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "role", nullable = false, length = 20)
    open var role: Role = Role.USER,

    @field:Column(name = "created_at", nullable = false, updatable = false)
    open var createdAt: Instant = Instant.now(),

    @field:Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()
) {

    @PrePersist
    fun prePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}