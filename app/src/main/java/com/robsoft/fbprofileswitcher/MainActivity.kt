package com.robsoft.fbprofileswitcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsoft.fbprofileswitcher.ui.theme.FBProfileSwitcherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

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
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchData() {
        scope.launch(Dispatchers.IO) {
            val current = executeRootCommand("cat /data/data/com.facebook.katana/accountid.txt").trim()
            val list = executeRootCommand("ls /data/data/profiles/fb/").split("\n").filter { it.isNotBlank() }

            withContext(Dispatchers.Main) {
                currentProfile = if (current.isEmpty() || current.startsWith("cat:") || current.startsWith("Error")) "None/Unknown" else current
                availableProfiles = list
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text(text = "Current profile:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = currentProfile,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (!isBusy) {
                                    isBusy = true
                                    val currentId = currentProfile // Capture the ID known to the app
                                    scope.launch(Dispatchers.IO) {
                                        switchFBProfile(profile, currentId)
                                        fetchData()
                                        withContext(Dispatchers.Main) {
                                            isBusy = false
                                        }
                                    }
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = profile,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

fun switchFBProfile(targetProfileName: String, currentProfileId: String) {
    if (currentProfileId == "None/Unknown" || currentProfileId == "Loading..." || currentProfileId.isNotBlank()) {
        return
    }

    val fullCommand = "am force-stop com.facebook.katana && " +
            "mv /data/data/com.facebook.katana /data/data/profiles/fb/\"$currentProfileId\" && " +
            "mv /data/data/profiles/fb/\"$targetProfileName\" /data/data/com.facebook.katana && " +
            "restorecon -R /data/data/com.facebook.katana"

    try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$fullCommand\n")
        os.writeBytes("exit\n")
        os.flush()
        process.waitFor()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun executeRootCommand(command: String): String {
    return try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$command\n")
        os.writeBytes("exit\n")
        os.flush()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        process.waitFor()
        output.toString()
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
