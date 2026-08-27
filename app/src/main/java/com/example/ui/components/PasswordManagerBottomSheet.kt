package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavedCredential
import com.example.ui.theme.GeoBackground
import com.example.ui.theme.GeoError
import com.example.ui.theme.GeoOnBackground
import com.example.ui.theme.GeoOnSurfaceVariant
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoPrimary
import com.example.ui.theme.GeoPrimaryContainer
import com.example.ui.theme.GeoSurface
import com.example.ui.theme.GeoSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerBottomSheet(
    credentials: List<SavedCredential>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onAutofillAccount: (SavedCredential) -> Unit,
    onSaveCredential: (accountLabel: String, username: String, pass: String, role: String, autoSubmit: Boolean, notes: String, id: Long) -> Unit,
    onDeleteCredential: (SavedCredential) -> Unit,
    autoFillOnLogout: Boolean = true,
    onToggleAutoFillOnLogout: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingCredential by remember { mutableStateOf<SavedCredential?>(null) }
    var credentialToDelete by remember { mutableStateOf<SavedCredential?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GeoSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("password_manager_sheet")
        ) {
            // Header with Vagabond Logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VagabondLogoBadge(
                        size = 44.dp,
                        showRegistrationText = false
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Saved Passwords & Logins",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = GeoOnBackground
                        )
                        Text(
                            text = "${credentials.size} account(s) saved for auto-login",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeoOnSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        editingCredential = null
                        showAddEditDialog = true
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEA580C),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("add_new_password_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Auto-Fill on Logout / Login Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (autoFillOnLogout) Color(0xFFFFF7ED) else Color(0xFFF1F5F9)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (autoFillOnLogout) Color(0xFFFED7AA) else Color(0xFFE2E8F0)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Fill on Logout & Login",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = GeoOnBackground
                        )
                        Text(
                            text = "Keep last username & password filled in the boxes ready for 1-tap Login",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeoOnSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoFillOnLogout,
                        onCheckedChange = { onToggleAutoFillOnLogout(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFEA580C)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = GeoOutlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            if (credentials.isEmpty()) {
                // Empty state with quick sample presets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFFEA580C).copy(alpha = 0.6f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No saved passwords yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = GeoOnBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Save Admin, Rider, or Staff credentials once and never enter them again!",
                        style = MaterialTheme.typography.bodySmall,
                        color = GeoOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            editingCredential = null
                            showAddEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEA580C)
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("empty_state_add_account_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save First Account")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(credentials, key = { it.id }) { credential ->
                        CredentialItemCard(
                            credential = credential,
                            onAutofill = {
                                onAutofillAccount(credential)
                                onDismiss()
                            },
                            onEdit = {
                                editingCredential = credential
                                showAddEditDialog = true
                            },
                            onDelete = {
                                credentialToDelete = credential
                            },
                            onCopyUsername = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Username", credential.username)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Username copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onCopyPassword = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Password", credential.password)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Account Dialog
    if (showAddEditDialog) {
        AddEditCredentialDialog(
            initialCredential = editingCredential,
            onDismiss = { showAddEditDialog = false },
            onSave = { label, user, pass, role, autoSubmit, notes, id ->
                onSaveCredential(label, user, pass, role, autoSubmit, notes, id)
                showAddEditDialog = false
                Toast.makeText(context, "Account credentials saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Confirm Delete Dialog
    if (credentialToDelete != null) {
        val toDelete = credentialToDelete!!
        AlertDialog(
            onDismissRequest = { credentialToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = GeoError
                )
            },
            title = {
                Text(
                    text = "Delete Saved Account?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to remove '${toDelete.accountLabel}' (${toDelete.username})?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCredential(toDelete)
                        credentialToDelete = null
                        Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeoError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { credentialToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CredentialItemCard(
    credential: SavedCredential,
    onAutofill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    val roleColor = when (credential.role.uppercase()) {
        "ADMIN" -> Color(0xFFDC2626)
        "RIDER" -> Color(0xFF16A34A)
        "MANAGER" -> Color(0xFF0284C7)
        else -> Color(0xFFEA580C)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
            .border(1.dp, GeoOutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(roleColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (credential.role.uppercase()) {
                                "ADMIN" -> Icons.Default.AdminPanelSettings
                                "RIDER" -> Icons.Default.TwoWheeler
                                else -> Icons.Default.Person
                            },
                            contentDescription = null,
                            tint = roleColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = credential.accountLabel,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = GeoOnBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = roleColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = credential.role.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = roleColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Account",
                            tint = GeoOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Account",
                            tint = GeoError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Username Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Username / Email",
                        fontSize = 10.sp,
                        color = GeoOnSurfaceVariant
                    )
                    Text(
                        text = credential.username,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GeoOnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCopyUsername,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Username",
                        tint = GeoOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Password Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Password",
                        fontSize = 10.sp,
                        color = GeoOnSurfaceVariant
                    )
                    Text(
                        text = if (isPasswordVisible) credential.password else "••••••••••••",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GeoOnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                            tint = GeoOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onCopyPassword,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Password",
                            tint = GeoOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1-Tap Auto-fill & Log In Button
            Button(
                onClick = onAutofill,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("autofill_account_${credential.id}"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEA580C),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto-fill & Log In as ${credential.accountLabel}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

@Composable
fun AddEditCredentialDialog(
    initialCredential: SavedCredential?,
    onDismiss: () -> Unit,
    onSave: (label: String, username: String, pass: String, role: String, autoSubmit: Boolean, notes: String, id: Long) -> Unit
) {
    var accountLabel by remember { mutableStateOf(initialCredential?.accountLabel ?: "") }
    var username by remember { mutableStateOf(initialCredential?.username ?: "") }
    var password by remember { mutableStateOf(initialCredential?.password ?: "") }
    var selectedRole by remember { mutableStateOf(initialCredential?.role ?: "USER") }
    var autoSubmit by remember { mutableStateOf(initialCredential?.autoSubmit ?: true) }
    var notes by remember { mutableStateOf(initialCredential?.notes ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val roles = listOf("ADMIN", "RIDER", "MANAGER", "USER")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCredential != null) "Edit Saved Account" else "Save Account Password",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Role Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    roles.forEach { role ->
                        FilterChip(
                            selected = selectedRole.equals(role, ignoreCase = true),
                            onClick = {
                                selectedRole = role
                                if (accountLabel.isBlank() || roles.contains(accountLabel.uppercase())) {
                                    accountLabel = when (role) {
                                        "ADMIN" -> "Admin Portal"
                                        "RIDER" -> "Rider Account"
                                        "MANAGER" -> "Manager Portal"
                                        else -> "My Account"
                                    }
                                }
                            },
                            label = { Text(role, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFED7AA),
                                selectedLabelColor = Color(0xFFC2410C)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = accountLabel,
                    onValueChange = { accountLabel = it },
                    label = { Text("Account Label (e.g. Admin, Rider)") },
                    placeholder = { Text("e.g. Master Admin") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or Email") },
                    placeholder = { Text("Enter login ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter password") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-submit on tap",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Automatically click login button after filling",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeoOnSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSubmit,
                        onCheckedChange = { autoSubmit = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFEA580C)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotBlank() && password.isNotBlank()) {
                        onSave(
                            accountLabel.ifBlank { selectedRole },
                            username.trim(),
                            password,
                            selectedRole,
                            autoSubmit,
                            notes,
                            initialCredential?.id ?: 0L
                        )
                    }
                },
                enabled = username.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
            ) {
                Text("Save Credentials")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
