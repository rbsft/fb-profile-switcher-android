package com.robsoft.fbprofileswitcher

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsoft.fbprofileswitcher.ui.theme.FBProfileSwitcherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FBProfileSwitcherTheme {
                ProfileSwitcherScreen()
            }
        }
    }
}

@Composable
fun ProfileSwitcherScreen() {
    var currentProfile by remember { mutableStateOf("Loading...") }
    var availableProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var storageInfo by remember { mutableStateOf("Calculating...") }
    var isBusy by remember { mutableStateOf(false) }
    var showInitDialog by remember { mutableStateOf(false) }
    var initName by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun getStorageStats(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            val total = totalBlocks * blockSize
            val available = availableBlocks * blockSize
            
            "Free: ${formatSize(available)} / Total: ${formatSize(total)}"
        } catch (e: Exception) {
            "Storage info unavailable"
        }
    }

    fun fetchData() {
        scope.launch(Dispatchers.IO) {
            val current = executeRootCommand("cat /data/data/com.facebook.katana/accountid.txt").trim()
            val list = executeRootCommand("ls /data/data/profiles/fb/").split("\n").filter { it.isNotBlank() }
            val storage = getStorageStats()

            withContext(Dispatchers.Main) {
                currentProfile = if (current.isEmpty() || current.startsWith("cat:") || current.startsWith("Error")) "None/Unknown" else current
                availableProfiles = list
                storageInfo = storage
                if (currentProfile == "None/Unknown" && !isBusy) {
                    showInitDialog = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

    fun runAndNotify(vararg commands: String) {
        isBusy = true
        scope.launch(Dispatchers.IO) {
            val outputs = mutableListOf<String>()
            for (command in commands) {
                val output = executeRootCommand(command)
                if (output.isNotEmpty()) {
                    outputs.add(output)
                }
            }
            withContext(Dispatchers.Main) {
                if (outputs.isNotEmpty()) {
                    val fullOutput = outputs.joinToString("; ")
                    snackbarHostState.showSnackbar(
                        message = if (fullOutput.length > 100) fullOutput.take(100) + "..." else fullOutput,
                        duration = SnackbarDuration.Long
                    )
                }
                fetchData()
                isBusy = false
            }
        }
    }

    if (showInitDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Initialize Facebook profiles") },
            text = {
                Column {
                    Text("Enter name for the current profile:")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = initName,
                        onValueChange = { initName = it },
                        placeholder = { Text("e.g. Personal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = initName.isNotBlank(),
                    onClick = {
                        showInitDialog = false
                        runAndNotify(
                            "mkdir -p /data/data/com.facebook.katana",
                            "echo '$initName' > /data/data/com.facebook.katana/accountid.txt",
                            "mkdir -p /data/data/profiles/fb/",
                            "restorecon -R /data/data/com.facebook.katana"
                        )
                    }
                ) { Text("Initialize") }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Profile") },
            text = {
                Column {
                    Text("Enter name for the new profile folder:")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = createName,
                        onValueChange = { createName = it },
                        placeholder = { Text("e.g. Work") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = createName.isNotBlank(),
                    onClick = {
                        showCreateDialog = false
                        runAndNotify(
                            "am force-stop com.facebook.katana",
                            "mkdir -p /data/data/profiles/fb/\"$createName\"",
                            "echo '$createName' > /data/data/profiles/fb/\"$createName\"/accountid.txt"
                        )
                        createName = ""
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Profile") },
            text = { Text("Are you sure you want to delete profile '$profileToDelete'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        runAndNotify("rm -rf /data/data/profiles/fb/\"$profileToDelete\"")
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text(text = "Current profile:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = currentProfile,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = storageInfo,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Text("Create Profile")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp)) {
            Text(text = "Available profiles:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            if (availableProfiles.isEmpty() && !isBusy) {
                Text(text = "No profiles found in /data/data/profiles/fb/", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(availableProfiles) { profile ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = profile, modifier = Modifier.weight(1f), fontSize = 16.sp)
                            TextButton(
                                onClick = {
                                    runAndNotify(
                                        "am force-stop com.facebook.katana",
                                        "rm -rf /data/data/profiles/fb/\"$currentProfile\"",
                                        "mv /data/data/com.facebook.katana /data/data/profiles/fb/\"$currentProfile\"",
                                        "rm -rf /data/data/com.facebook.katana",
                                        "mv /data/data/profiles/fb/\"$profile\" /data/data/com.facebook.katana",
                                        "restorecon -R /data/data/com.facebook.katana"
                                    )
                                },
                                enabled = !isBusy && currentProfile != "None/Unknown"
                            ) { Text("Switch") }
                            TextButton(
                                onClick = {
                                    profileToDelete = profile
                                    showDeleteDialog = true
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

fun executeRootCommand(command: String): String {
    val tag = "FBProfileSwitcher"
    return try {
        Log.d(tag, "Executing: su -c '$command'")
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        
        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        while (errorReader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        
        process.waitFor()
        val result = output.toString().trim()
        if (result.isNotEmpty()) Log.d(tag, "Result: $result")
        result
    } catch (e: Exception) {
        Log.e(tag, "Error: ${e.message}")
        "Error: ${e.message}"
    }
}
