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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.GeoError
import com.example.ui.theme.GeoOnBackground
import com.example.ui.theme.GeoOnSurfaceVariant
import com.example.ui.theme.GeoOutlineVariant
import com.example.ui.theme.GeoSurface

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
    var searchQuery by remember { mutableStateOf("") }

    val filteredCredentials = credentials.filter {
        it.accountLabel.contains(searchQuery, ignoreCase = true) ||
        it.username.contains(searchQuery, ignoreCase = true)
    }

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
            // Header with KeePass / Password Vault identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFEA580C), Color(0xFFC2410C))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Password Safe Vault",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = GeoOnBackground
                        )
                        Text(
                            text = "${credentials.size} account(s) saved",
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Password",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            if (credentials.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_input"),
                    placeholder = { Text("Search saved accounts by label or username...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = GeoOnSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = GeoOnSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // List of Accounts
            if (credentials.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFED7AA))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEDD5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Passwords Saved Yet",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = GeoOnBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create an account entry by providing the Account Label, Username, and Password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeoOnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                editingCredential = null
                                showAddEditDialog = true
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Saved Account")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCredentials, key = { it.id }) { cred ->
                        KeePassAccountCard(
                            credential = cred,
                            onAutofill = {
                                onAutofillAccount(cred)
                                onDismiss()
                            },
                            onEdit = {
                                editingCredential = cred
                                showAddEditDialog = true
                            },
                            onDelete = {
                                credentialToDelete = cred
                            },
                            onCopyUsername = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Username", cred.username))
                                Toast.makeText(context, "Username copied!", Toast.LENGTH_SHORT).show()
                            },
                            onCopyPassword = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Password", cred.password))
                                Toast.makeText(context, "Password copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog (Account Label, Username, Password, Save)
    if (showAddEditDialog) {
        AddEditPasswordDialog(
            initialCredential = editingCredential,
            onDismiss = { showAddEditDialog = false },
            onSave = { label, username, pass ->
                onSaveCredential(
                    label,
                    username,
                    pass,
                    "USER",
                    true,
                    "",
                    editingCredential?.id ?: 0L
                )
                showAddEditDialog = false
                Toast.makeText(context, "Saved account: $label", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    if (credentialToDelete != null) {
        AlertDialog(
            onDismissRequest = { credentialToDelete = null },
            title = { Text("Delete Saved Password?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove the password for '${credentialToDelete?.accountLabel}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        credentialToDelete?.let { onDeleteCredential(it) }
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
fun KeePassAccountCard(
    credential: SavedCredential,
    onAutofill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_card_${credential.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Label & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF7ED))
                            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = credential.accountLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = GeoOnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

            Spacer(modifier = Modifier.height(10.dp))

            // Username Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
                        text = credential.username.ifBlank { "—" },
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
                    .background(Color(0xFFF1F5F9))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Tap Auto-fill & Log In Button
            Button(
                onClick = onAutofill,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
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
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Apply & Log In as ${credential.accountLabel}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

/**
 * Clean KeePass-style Add/Edit dialog where user explicitly enters:
 * 1. Account Label / Name
 * 2. Username / Email
 * 3. Password
 * and clicks Save.
 */
@Composable
fun AddEditPasswordDialog(
    initialCredential: SavedCredential?,
    onDismiss: () -> Unit,
    onSave: (label: String, username: String, pass: String) -> Unit
) {
    var accountLabel by remember { mutableStateOf(initialCredential?.accountLabel ?: "") }
    var username by remember { mutableStateOf(initialCredential?.username ?: "") }
    var password by remember { mutableStateOf(initialCredential?.password ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Color(0xFFEA580C),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialCredential != null) "Edit Saved Password" else "Save New Password",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Account Label
                OutlinedTextField(
                    value = accountLabel,
                    onValueChange = { accountLabel = it },
                    label = { Text("Account Label / Name") },
                    placeholder = { Text("e.g. My Login, Rider Portal, Admin") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_account_label")
                )

                // Username / Email
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or Email") },
                    placeholder = { Text("Enter your username or email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_username")
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter your password") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_password")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (accountLabel.isNotBlank() || username.isNotBlank()) {
                        onSave(
                            accountLabel.ifBlank { username },
                            username.trim(),
                            password
                        )
                    }
                },
                enabled = (accountLabel.isNotBlank() || username.isNotBlank()) && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                modifier = Modifier.testTag("dialog_save_password_button")
            ) {
                Text("Save Password", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
