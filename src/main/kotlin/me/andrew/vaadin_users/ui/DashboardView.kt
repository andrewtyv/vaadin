
package me.andrew.vaadin_users.ui

import com.github.mvysny.karibudsl.v10.button
import com.github.mvysny.karibudsl.v10.comboBox
import com.github.mvysny.karibudsl.v10.emailField
import com.github.mvysny.karibudsl.v10.grid
import com.github.mvysny.karibudsl.v10.h1
import com.github.mvysny.karibudsl.v10.h2
import com.github.mvysny.karibudsl.v10.horizontalLayout
import com.github.mvysny.karibudsl.v10.onLeftClick
import com.github.mvysny.karibudsl.v10.p
import com.github.mvysny.karibudsl.v10.passwordField
import com.github.mvysny.karibudsl.v10.textField
import com.github.mvysny.karibudsl.v10.verticalLayout
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.confirmdialog.ConfirmDialog
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.security.AuthenticationContext
import jakarta.annotation.security.PermitAll
import me.andrew.vaadin_users.domain.AppUser
import me.andrew.vaadin_users.domain.Role
import me.andrew.vaadin_users.service.UserSearchType
import me.andrew.vaadin_users.service.UserService
import org.springframework.data.domain.Sort
import org.springframework.security.core.context.SecurityContextHolder
import com.vaadin.flow.component.button.Button

@Route("")
@PageTitle("Dashboard")
@PermitAll
class DashboardView(
    private val userService: UserService,
    private val authenticationContext: AuthenticationContext
) : VerticalLayout() {


    private lateinit var grid: Grid<AppUser>
    private lateinit var searchField: TextField
    private lateinit var searchTypeCombo: ComboBox<String>
    private lateinit var sortByCombo: ComboBox<String>
    private lateinit var pageInfo: Paragraph

    private lateinit var createButton: com.vaadin.flow.component.button.Button
    private lateinit var editButton: com.vaadin.flow.component.button.Button
    private lateinit var deleteButton: com.vaadin.flow.component.button.Button
    private lateinit var previousButton: com.vaadin.flow.component.button.Button
    private lateinit var nextButton: com.vaadin.flow.component.button.Button

    private var currentPage = 0
    private var pageSize = 25
    private var sortDirection = Sort.Direction.ASC
    private var selectedUser: AppUser? = null

    private val sortOptions = linkedMapOf(
        "Username" to "username",
        "Email" to "email",
        "Creation date" to "createdAt",
        "Last update date" to "updatedAt"
    )

    init {
        setSizeFull()
        isPadding = true
        isSpacing = true

        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("Authentication is missing")

        val username = authentication.name ?: "unknown"
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" }


        h1("User Dashboard")
        p("Logged in as: $username")
        p("Role: ${if (isAdmin) "ADMIN" else "USER"}")


        horizontalLayout {
            isSpacing = true
            button("Logout") {
                addThemeVariants(ButtonVariant.LUMO_ERROR)
                onLeftClick { authenticationContext.logout() }
            }
        }

        h2("Users")

        horizontalLayout {
            setWidthFull()
            isSpacing = true

            searchField = textField("Search") {
                placeholder = "Search"
                valueChangeMode = ValueChangeMode.LAZY
                isClearButtonVisible = true
                setWidthFull()
                addValueChangeListener {
                    currentPage = 0
                    refreshGrid()
                }
            }

            searchTypeCombo = comboBox("Search by") {
                setItems("Username", "Email")
                value = "Username"
                isAllowCustomValue = false
                addValueChangeListener {
                    currentPage = 0
                    refreshGrid()
                }
            }

            sortByCombo = comboBox("Sort by") {
                setItems(sortOptions.keys)
                value = "Username"
                isAllowCustomValue = false
                addValueChangeListener {
                    currentPage = 0
                    refreshGrid()
                }
            }

            button("ASC") {
                onLeftClick {
                    sortDirection = if (sortDirection == Sort.Direction.ASC)
                        Sort.Direction.DESC else Sort.Direction.ASC
                    text = sortDirection.name
                    currentPage = 0
                    refreshGrid()
                }
            }

            comboBox<Int>("Page size") {
                setItems(listOf(10, 25, 50, 100))
                value = pageSize
                isAllowCustomValue = false
                addValueChangeListener { event ->
                    pageSize = event.value ?: return@addValueChangeListener
                    currentPage = 0
                    refreshGrid()
                }
            }
        }
        if (isAdmin) {
            horizontalLayout {
                isSpacing = true
                createButton = button("Create user") {
                    addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                    onLeftClick { openUserDialog(null) }
                }
                editButton = button("Edit selected") {
                    isEnabled = false
                    onLeftClick {
                        val user = selectedUser ?: return@onLeftClick
                        openUserDialog(user)
                    }
                }
                deleteButton = button("Delete selected") {
                    isEnabled = false
                    addThemeVariants(ButtonVariant.LUMO_ERROR)
                    onLeftClick {
                        val user = selectedUser ?: return@onLeftClick
                        openDeleteDialog(user)
                    }
                }
            }
        } else {
            createButton = com.vaadin.flow.component.button.Button()
            editButton = com.vaadin.flow.component.button.Button()
            deleteButton = com.vaadin.flow.component.button.Button()
        }


        grid = grid<AppUser> {
            setSizeFull()
            addColumn { it.username }.setHeader("Username").setAutoWidth(true)
            addColumn { it.email }.setHeader("Email").setAutoWidth(true)
            addColumn { it.role }.setHeader("Role").setAutoWidth(true)
            addColumn { it.createdAt }.setHeader("Created At").setAutoWidth(true)
            addColumn { it.updatedAt }.setHeader("Updated At").setAutoWidth(true)

            asSingleSelect().addValueChangeListener { event ->
                selectedUser = event.value
                val hasSelection = selectedUser != null
                editButton.isEnabled = hasSelection
                deleteButton.isEnabled = hasSelection
            }
        }
        expand(grid)

        horizontalLayout {
            isSpacing = true
            defaultVerticalComponentAlignment = Alignment.CENTER

            previousButton = button("Previous") {
                onLeftClick {
                    if (currentPage > 0) {
                        currentPage--
                        refreshGrid()
                    }
                }
            }
            nextButton = button("Next") {
                onLeftClick {
                    currentPage++
                    refreshGrid()
                }
            }
            pageInfo = p()
        }

        refreshGrid()
    }

    private fun openUserDialog(user: AppUser?) {
        val isCreate = user == null

        val dialog = Dialog()
        dialog.headerTitle = if (isCreate) "Create user" else "Edit user"

        lateinit var errorParagraph: Paragraph

        val form = verticalLayout {
            isPadding = false
            isSpacing = true
            setWidth("400px")

            val usernameField = textField("Username") {
                setWidthFull()
                value = user?.username ?: ""
            }
            val emailField = emailField("Email") {
                setWidthFull()
                value = user?.email ?: ""

                addValueChangeListener {
                    val emailRegex = Regex("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")
                    isInvalid = it.value.isNotBlank() && !emailRegex.matches(it.value)
                    errorMessage = "Email is invalid (e.g. user@example.com)"
                }
            }
            val passwordField = passwordField(
                if (isCreate) "Password" else "New password (optional)"
            ) {
                setWidthFull()
            }
            val roleCombo = comboBox<Role>("Role") {
                setItems(Role.entries.toList())
                value = user?.role ?: Role.USER
                setWidthFull()
            }
            errorParagraph = p {
                style.set("color", "var(--lumo-error-text-color)")
            }

            dialog.footer.add(
                Button("Cancel").apply {
                    onLeftClick { dialog.close() }
                },
                Button(if (isCreate) "Create" else "Save").apply {
                    addThemeVariants(ButtonVariant.LUMO_PRIMARY)  // тепер Button.addThemeVariants ✓
                    onLeftClick {
                        try {
                            val selectedRole = roleCombo.value
                                ?: throw IllegalArgumentException("Role is required")

                            if (isCreate) {
                                userService.createUser(
                                    username = usernameField.value,
                                    email = emailField.value,
                                    rawPassword = passwordField.value,
                                    role = selectedRole
                                )
                                Notification.show("User created")
                            } else {
                                val userId = user?.id
                                    ?: throw IllegalArgumentException("User id is missing")
                                userService.updateUser(
                                    id = userId,
                                    username = usernameField.value,
                                    email = emailField.value,
                                    rawPassword = passwordField.value,
                                    role = selectedRole
                                )
                                Notification.show("User updated")
                            }
                            dialog.close()
                            refreshGrid()
                        } catch (e: Exception) {
                            errorParagraph.text = e.message ?: "Operation failed"
                        }
                    }
                }
            )
        }

        dialog.add(form)
        dialog.open()
    }

    private fun openDeleteDialog(user: AppUser) {
        ConfirmDialog().apply {
            setHeader("Delete user")
            setText("Are you sure you want to delete user '${user.username}'?")
            setCancelable(true)
            setConfirmText("Delete")
            setConfirmButtonTheme("error primary")
            addConfirmListener {
                try {
                    val userId = user.id
                        ?: throw IllegalArgumentException("User id is missing")
                    userService.deleteUser(userId)
                    Notification.show("User deleted")
                    refreshGrid()
                } catch (e: Exception) {
                    Notification.show(e.message ?: "Delete failed")
                }
            }
            open()
        }
    }

    private fun refreshGrid() {
        val sortProperty = sortOptions[sortByCombo.value] ?: "username"
        val searchType = when (searchTypeCombo.value) {
            "Email" -> UserSearchType.EMAIL
            else -> UserSearchType.USERNAME
        }

        var page = userService.findUsers(
            query = searchField.value ?: "",
            searchType = searchType,
            page = currentPage,
            size = pageSize,
            sortProperty = sortProperty,
            sortDirection = sortDirection
        )

        if (page.totalPages > 0 && currentPage >= page.totalPages) {
            currentPage = page.totalPages - 1
            page = userService.findUsers(
                query = searchField.value ?: "",
                searchType = searchType,
                page = currentPage,
                size = pageSize,
                sortProperty = sortProperty,
                sortDirection = sortDirection
            )
        }

        grid.setItems(page.content)

        selectedUser = null
        editButton.isEnabled = false
        deleteButton.isEnabled = false

        pageInfo.text = if (page.totalPages == 0) {
            currentPage = 0
            "No users found"
        } else {
            "Page ${currentPage + 1} of ${page.totalPages}, total users: ${page.totalElements}"
        }

        previousButton.isEnabled = currentPage > 0
        nextButton.isEnabled = currentPage + 1 < page.totalPages
    }
}
