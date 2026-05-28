package me.andrew.vaadin_users

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VaadinUsersApplication

fun main(args: Array<String>) {
	runApplication<VaadinUsersApplication>(*args)
}