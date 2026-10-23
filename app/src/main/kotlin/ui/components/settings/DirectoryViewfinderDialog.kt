package io.github.roaxio.dashguardpro.ui.components.settings

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.HudBlack
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen
import io.github.roaxio.dashguardpro.ui.theme.SafetyAmber
import java.io.File

/**
 * Interactive In-App Folder Explorer & Viewfinder Dialog
 * Allows users to visually browse device storage, create folders, or paste directory paths.
 */
@Composable
fun DirectoryViewfinderDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onConfirmFolder: (String) -> Unit,
    onLaunchSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Viewfinder Explorer, 1: Paste Path

    // Current explorer directory state
    var currentDirectory by remember {
        val file = if (initialPath.isNotBlank()) File(initialPath) else StorageDirectoryManager.getBrowserRootDirectory()
        val validFile = if (file.exists() && file.isDirectory) file else StorageDirectoryManager.getBrowserRootDirectory()
        mutableStateOf(validFile)
    }

    var subdirectories by remember { mutableStateOf(StorageDirectoryManager.listSubdirectories(currentDirectory)) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // Manual input state
    var manualPathInput by remember(initialPath) {
        val defaultPath = if (initialPath.isNotBlank()) initialPath else currentDirectory.absolutePath
        mutableStateOf(defaultPath)
    }

    // Refresh directory contents when currentDirectory changes
    LaunchedEffect(currentDirectory) {
        subdirectories = StorageDirectoryManager.listSubdirectories(currentDirectory)
        manualPathInput = currentDirectory.absolutePath
    }

    // Intercept phone system hardware back button inside Directory Viewfinder
    androidx.activity.compose.BackHandler {
        if (showNewFolderDialog) {
            showNewFolderDialog = false
        } else {
            val rootDir = StorageDirectoryManager.getBrowserRootDirectory()
            val parent = currentDirectory.parentFile
            if (parent != null && parent.canRead() && currentDirectory.absolutePath != rootDir.absolutePath && currentDirectory.absolutePath != "/storage/emulated/0" && currentDirectory.absolutePath != "/storage/emulated") {
                currentDirectory = parent
            } else {
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1322)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .testTag("directory_viewfinder_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dialog Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HudBlack)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Folder",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "CUSTOM VIDEO DIRECTORY",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Select or paste target storage location",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tab Switcher (Viewfinder vs Manual Paste)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF0F172A),
                    contentColor = CyberCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CyberCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Folder Viewfinder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste / Manual Path", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                // Main Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    if (selectedTab == 0) {
                        // ==========================================
                        // TAB 0: IN-APP DIRECTORY VIEWFINDER
                        // ==========================================
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Current Breadcrumb Navigation Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0B1120))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val hasParent = currentDirectory.parentFile != null && currentDirectory.parentFile?.canRead() == true
                                IconButton(
                                    onClick = {
                                        currentDirectory.parentFile?.let { parent ->
                                            if (parent.exists() && parent.canRead()) {
                                                currentDirectory = parent
                                            }
                                        }
                                    },
                                    enabled = hasParent,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Up",
                                        tint = if (hasParent) CyberCyan else Color(0xFF475569),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentDirectory.name.ifBlank { "/" },
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentDirectory.absolutePath,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp,
                                        softWrap = true
                                    )
                                }

                                IconButton(
                                    onClick = { showNewFolderDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreateNewFolder,
                                        contentDescription = "New Folder",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Subdirectories List
                            if (subdirectories.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No subfolders found",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap 'Use This Folder' or create a new subfolder",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    items(subdirectories) { subFolder ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (subFolder.canRead()) {
                                                        currentDirectory = subFolder
                                                    } else {
                                                        Toast.makeText(context, "Cannot access folder: Permission required", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Folder",
                                                tint = CyberCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = subFolder.name,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val itemCount = try { subFolder.list()?.size ?: 0 } catch (_: Exception) { 0 }
                                                Text(
                                                    text = "$itemCount items",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Bottom Actions Row for Viewfinder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onLaunchSystemPicker,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("System Picker", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        onConfirmFolder(currentDirectory.absolutePath)
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("btn_select_viewfinder_folder"),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Use This Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // ==========================================
                        // TAB 1: PASTE / MANUAL PATH INPUT
                        // ==========================================
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Enter or paste the absolute directory path where recorded video loops and locked clips should be stored:",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = manualPathInput,
                                onValueChange = { manualPathInput = it },
                                label = { Text("Directory Absolute Path", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("/storage/emulated/0/DashGuard", color = Color(0xFF64748B)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color(0xFFE2E8F0),
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    cursorColor = CyberCyan
                                ),
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_manual_directory_path")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Paste from Clipboard Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                                            if (text.isNotBlank()) {
                                                manualPathInput = text
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "No text in clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste from Clipboard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Live Path Status Check
                            val enteredFile = File(manualPathInput.trim())
                            val exists = enteredFile.exists()
                            val canWrite = try { enteredFile.canWrite() || (enteredFile.parentFile?.canWrite() == true) } catch (_: Exception) { false }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (exists && canWrite) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (exists && canWrite) NeonGreen else SafetyAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (exists && canWrite) "Path Exists & Writable" else if (!exists) "Folder will be auto-created on save" else "Folder read-only or restricted",
                                            color = if (exists && canWrite) NeonGreen else SafetyAmber,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = manualPathInput.ifBlank { "No path specified" },
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp,
                                            softWrap = true
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Confirm Button
                            Button(
                                onClick = {
                                    val trimmed = manualPathInput.trim()
                                    if (trimmed.isNotBlank()) {
                                        onConfirmFolder(trimmed)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_confirm_manual_folder"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Apply Folder", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // "Create New Subfolder" Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Text("Create New Folder", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Create folder inside: ${currentDirectory.name}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("DashcamFootage", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0),
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF334155),
                            cursorColor = CyberCyan
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = StorageDirectoryManager.createSubdirectory(currentDirectory, newFolderName)
                        result.onSuccess { createdDir ->
                            currentDirectory = createdDir
                            subdirectories = StorageDirectoryManager.listSubdirectories(createdDir)
                            showNewFolderDialog = false
                            newFolderName = ""
                        }.onFailure { err ->
                            Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}
