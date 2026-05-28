package me.andrew.vaadin_users.ui

import com.vaadin.flow.component.UI
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.confirmdialog.ConfirmDialog
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.EmailField
import com.vaadin.flow.component.textfield.PasswordField
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

@Route("")
@PageTitle("Dashboard")
@PermitAll
class DashboardView(
    private val userService: UserService,
    private val authenticationContext: AuthenticationContext

) : VerticalLayout() {

    private val grid = Grid(AppUser::class.java, false)

    private val searchField = TextField("Search")
    private val pageSizeCombo = ComboBox<Int>("Page size")
    private val sortByCombo = ComboBox<String>("Sort by")
    private val sortDirectionButton = Button("ASC")

    private val createButton = Button("Create user")
    private val editButton = Button("Edit selected")
    private val deleteButton = Button("Delete selected")

    private val previousButton = Button("Previous")
    private val nextButton = Button("Next")
    private val pageInfo = Paragraph()

    private var currentPage = 0
    private var pageSize = 25
    private var sortDirection = Sort.Direction.ASC
    private var selectedUser: AppUser? = null

    private val searchTypeCombo = ComboBox<String>("Search by")

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
        val authorities = authentication.authorities.mapNotNull { it.authority }
        val isAdmin = authorities.contains("ROLE_ADMIN")

        val title = H1("User Dashboard")
        val loggedInInfo = Paragraph("Logged in as: $username")
        val roleInfo = Paragraph("Role: ${if (isAdmin) "ADMIN" else "USER"}")

        val logoutButton = Button("Logout") {
            authenticationContext.logout()
        }
        logoutButton.addThemeVariants(ButtonVariant.LUMO_ERROR)

        configureSearch()
        configureSorting()
        configurePagination()
        configureGrid()
        configureAdminButtons()

        val topBar = HorizontalLayout(logoutButton)
        topBar.isSpacing = true

        val controls = HorizontalLayout(
            searchField,
            searchTypeCombo,
            sortByCombo,
            sortDirectionButton,
            pageSizeCombo
        )
        controls.setWidthFull()
        controls.isSpacing = true
        controls.setFlexGrow(1.0, searchField)

        val paginationBar = HorizontalLayout(
            previousButton,
            nextButton,
            pageInfo
        )
        paginationBar.isSpacing = true
        paginationBar.defaultVerticalComponentAlignment = Alignment.CENTER

        add(
            title,
            loggedInInfo,
            roleInfo,
            topBar,
            H2("Users"),
            controls
        )

        if (isAdmin) {
            val adminActions = HorizontalLayout(
                createButton,
                editButton,
                deleteButton
            )
            adminActions.isSpacing = true
            add(adminActions)
        }

        add(grid, paginationBar)
        expand(grid)

        refreshGrid()
    }

    private fun configureSearch() {
        searchField.placeholder = "Search"
        searchField.valueChangeMode = ValueChangeMode.LAZY
        searchField.isClearButtonVisible = true
        searchField.setWidthFull()

        searchTypeCombo.setItems("Username", "Email")
        searchTypeCombo.value = "Username"
        searchTypeCombo.isAllowCustomValue = false

        searchTypeCombo.addValueChangeListener {
            currentPage = 0
            refreshGrid()
        }

        searchField.addValueChangeListener {
            currentPage = 0
            refreshGrid()
        }
    }

    private fun configureSorting() {
        sortByCombo.setItems(sortOptions.keys)
        sortByCombo.value = "Username"
        sortByCombo.isAllowCustomValue = false

        sortByCombo.addValueChangeListener {
            currentPage = 0
            refreshGrid()
        }

        sortDirectionButton.addClickListener {
            sortDirection = if (sortDirection == Sort.Direction.ASC) {
                Sort.Direction.DESC
            } else {
                Sort.Direction.ASC
            }

            sortDirectionButton.text = sortDirection.name
            currentPage = 0
            refreshGrid()
        }
    }

    private fun configurePagination() {
        pageSizeCombo.setItems(listOf(10, 25, 50, 100))
        pageSizeCombo.value = pageSize
        pageSizeCombo.isAllowCustomValue = false

        pageSizeCombo.addValueChangeListener { event ->
            val selectedSize = event.value ?: return@addValueChangeListener
            pageSize = selectedSize
            currentPage = 0
            refreshGrid()
        }

        previousButton.addClickListener {
            if (currentPage > 0) {
                currentPage--
                refreshGrid()
            }
        }

        nextButton.addClickListener {
            currentPage++
            refreshGrid()
        }
    }

    private fun configureGrid() {
        grid.setSizeFull()

        grid.addColumn { it.username }
            .setHeader("Username")
            .setAutoWidth(true)

        grid.addColumn { it.email }
            .setHeader("Email")
            .setAutoWidth(true)

        grid.addColumn { it.role }
            .setHeader("Role")
            .setAutoWidth(true)

        grid.addColumn { it.createdAt }
            .setHeader("Created At")
            .setAutoWidth(true)

        grid.addColumn { it.updatedAt }
            .setHeader("Updated At")
            .setAutoWidth(true)

        grid.asSingleSelect().addValueChangeListener { event ->
            selectedUser = event.value
            val hasSelection = selectedUser != null

            editButton.isEnabled = hasSelection
            deleteButton.isEnabled = hasSelection
        }
    }

    private fun configureAdminButtons() {
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

        editButton.isEnabled = false
        deleteButton.isEnabled = false
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR)

        createButton.addClickListener {
            openUserDialog(null)
        }

        editButton.addClickListener {
            val user = selectedUser ?: return@addClickListener
            openUserDialog(user)
        }

        deleteButton.addClickListener {
            val user = selectedUser ?: return@addClickListener
            openDeleteDialog(user)
        }
    }

    private fun openUserDialog(user: AppUser?) {
        val isCreate = user == null

        val dialog = Dialog()
        dialog.headerTitle = if (isCreate) "Create user" else "Edit user"

        val usernameField = TextField("Username")
        usernameField.setWidthFull()
        usernameField.value = user?.username ?: ""

        val emailField = EmailField("Email")
        emailField.setWidthFull()
        emailField.value = user?.email ?: ""

        val passwordField = PasswordField(
            if (isCreate) "Password" else "New password (optional)"
        )
        passwordField.setWidthFull()

        val roleCombo = ComboBox<Role>("Role")
        roleCombo.setItems(Role.entries.toList())
        roleCombo.value = user?.role ?: Role.USER
        roleCombo.setWidthFull()

        val errorText = Paragraph()
        errorText.style.set("color", "var(--lumo-error-text-color)")

        val form = VerticalLayout(
            usernameField,
            emailField,
            passwordField,
            roleCombo,
            errorText
        )
        form.setPadding(false)
        form.setSpacing(true)
        form.setWidth("400px")

        val saveButton = Button(if (isCreate) "Create" else "Save") {
            try {
                val selectedRole = roleCombo.value
                    ?: throw IllegalArgumentException("Role is required")

                if (isCreate) {
                    userService.createUser(
                        username = usernameField.value ?: "",
                        email = emailField.value ?: "",
                        rawPassword = passwordField.value ?: "",
                        role = selectedRole
                    )
                    Notification.show("User created")
                } else {
                    val userId = user?.id
                        ?: throw IllegalArgumentException("User id is missing")

                    userService.updateUser(
                        id = userId,
                        username = usernameField.value ?: "",
                        email = emailField.value ?: "",
                        rawPassword = passwordField.value ?: "",
                        role = selectedRole
                    )
                    Notification.show("User updated")
                }

                dialog.close()
                refreshGrid()
            } catch (exception: Exception) {
                errorText.text = exception.message ?: "Operation failed"
            }
        }
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY)

        val cancelButton = Button("Cancel") {
            dialog.close()
        }

        dialog.add(form)
        dialog.footer.add(cancelButton, saveButton)
        dialog.open()
    }

    private fun openDeleteDialog(user: AppUser) {
        val dialog = ConfirmDialog()
        dialog.setHeader("Delete user")
        dialog.setText("Are you sure you want to delete user '${user.username}'?")
        dialog.setCancelable(true)
        dialog.setConfirmText("Delete")
        dialog.setConfirmButtonTheme("error primary")

        dialog.addConfirmListener {
            try {
                val userId = user.id
                    ?: throw IllegalArgumentException("User id is missing")

                userService.deleteUser(userId)

                Notification.show("User deleted")
                refreshGrid()
            } catch (exception: Exception) {
                Notification.show(exception.message ?: "Delete failed")
            }
        }

        dialog.open()
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

        val totalPages = page.totalPages
        val totalElements = page.totalElements

        if (totalPages == 0) {
            currentPage = 0
            pageInfo.text = "No users found"
        } else {
            pageInfo.text = "Page ${currentPage + 1} of $totalPages, total users: $totalElements"
        }

        previousButton.isEnabled = currentPage > 0
        nextButton.isEnabled = currentPage + 1 < totalPages
    }
}