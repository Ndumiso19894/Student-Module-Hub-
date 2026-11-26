package org.studentmodulehub.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Navy = Color(0xFF142A40)
private val Teal = Color(0xFF006B61)
private val Pale = Color(0xFFF3F7F6)
private val scheme = lightColorScheme(primary = Teal, secondary = Navy, background = Pale, surface = Color.White, onPrimary = Color.White)

class MainActivity : ComponentActivity() {
    private var callback by mutableStateOf<Pair<String, String>?>(null)
    private fun receive(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "org.studentmodulehub.app" && uri.host == "auth" && uri.path in listOf("/confirm", "/recovery")) {
            uri.getQueryParameter("code")?.let { callback = uri.path.orEmpty() to it }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        receive(intent)
        val backend = Backend(applicationContext)
        setContent { MaterialTheme(colorScheme = scheme) { HubApp(backend, callback) { callback = null } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp(backend: Backend, callback: Pair<String, String>?, clearCallback: () -> Unit) {
    var screen by remember { mutableStateOf("welcome") }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var demo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var qualification by remember { mutableStateOf<Qualification?>(null) }
    var qualifications by remember { mutableStateOf<List<Qualification>>(emptyList()) }
    var modules by remember { mutableStateOf<List<Module>>(emptyList()) }
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun clearMessage() { message = "" }
    fun go(value: String) { clearMessage(); screen = value }
    fun errorMessage(e: Exception): String = when (e) {
        is BackendException -> when (e.code) { 400, 401 -> "Request could not be completed. Check your details and backend settings."; 403 -> "Access denied. Check your account permissions."; 429 -> "Too many requests. Please try again later."; else -> "Service error (${e.code}). Please try again later." }
        is java.io.IOException -> "Could not connect. Check your internet connection and backend configuration."
        else -> "Request failed. Check your details, connection and email confirmation, then try again."
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; clearMessage()
        scope.launch { try { action() } catch (e: Exception) { message = errorMessage(e) } finally { busy = false } }
    }
    fun openQualifications() {
        go("qualifications"); qualifications = emptyList()
        if (demo) qualifications = listOf(Qualification("sample", "Diploma in Business Studies", "Sample qualification for navigation testing.", true))
        else run { qualifications = backend.qualifications() }
    }
    fun openModules(q: Qualification) {
        qualification = q; go("modules"); modules = emptyList()
        if (demo) modules = listOf(Module("sample-1", "Introduction to Management", "BUS101", "Sample module for navigation testing.", true), Module("sample-2", "Business Communication", "BUS102", "Sample module for navigation testing.", true))
        else run { modules = backend.modules(q.id) }
    }
    LaunchedEffect(Unit) {
        if (backend.configured && callback == null) {
            busy = true
            try { backend.restore()?.let { profile = it; screen = "home" } } catch (_: Exception) { message = "Could not restore your session. Please sign in." }
            finally { busy = false }
        }
    }
    LaunchedEffect(callback) {
        val incoming = callback ?: return@LaunchedEffect
        busy = true; message = ""
        try {
            backend.completeCallback(incoming.second)
            if (incoming.first == "/recovery") { profile = null; screen = "resetPassword" }
            else {
                val confirmedProfile = backend.restore()
                if (confirmedProfile != null) {
                    profile = confirmedProfile
                    screen = "home"
                    message = "Email confirmed. You are signed in."
                } else {
                    profile = null
                    screen = "login"
                    message = "Email confirmed. Please sign in to continue."
                }
            }
        } catch (_: Exception) { screen = "login"; message = "This link could not be verified. Request a new link on this device." }
        finally { busy = false; clearCallback() }
    }
    BackHandler(enabled = screen != "welcome" && !busy) {
        when (screen) {
            "login", "register", "forgot" -> go("welcome")
            "resetPassword" -> { run { backend.logout(); profile = null; screen = "login" } }
            "modules" -> go("qualifications")
            "moduleDetail" -> go("modules")
            "qualifications", "profile", "admin" -> go("home")
            "home" -> if (demo) { demo = false; go("welcome") } else go("profile")
            else -> go("welcome")
        }
    }
    var selectedModule by remember { mutableStateOf<Module?>(null) }
    Scaffold(containerColor = Pale, topBar = {
        if (screen !in listOf("welcome", "login", "register", "forgot", "resetPassword")) {
            CenterAlignedTopAppBar(
                title = { Text("Student Module Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                actions = { TextButton(onClick = { go("profile") }) { Text("Profile") } }
            )
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (demo) Notice("SAMPLE MODE · No account or live data", false)
            if (!backend.configured && !demo) Notice("Backend not configured. Sign-in and registration require a connected Supabase project.", false)
            if (message.isNotBlank()) Notice(message, true)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (screen) {
                "welcome" -> {
                    Spacer(Modifier.height(26.dp)); Brand()
                    Spacer(Modifier.height(12.dp))
                    Text("Your studies,\nall in one place.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Navy)
                    Text("A simple space to explore your qualifications and modules.", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF536477))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { go("login") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Student sign in") }
                    OutlinedButton(onClick = { go("register") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Create an account") }
                    TextButton(onClick = { demo = true; profile = null; go("home") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Explore sample screens") }
                    Text("Sample mode is for interface testing only. No account is created or signed in.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                "login" -> AuthScreen("Welcome back", "Sign in to continue your studies.", "Sign in", busy, false, onBack = { go("welcome") }, onSubmit = { _, email, pass ->
                    run { profile = backend.login(email, pass); demo = false; screen = "home" }
                }, onSecondary = { go("forgot") }, secondary = "Forgot password?")
                "register" -> AuthScreen("Create account", "Start your learning journey.", "Create account", busy, true, onBack = { go("welcome") }, onSubmit = { name, email, pass ->
                    run {
                        val createdProfile = backend.register(name, email, pass)
                        if (createdProfile != null) {
                            profile = createdProfile
                            demo = false
                            screen = "home"
                            message = "Your account has been created."
                        } else {
                            screen = "login"
                            message = "Registration submitted. Check your email for a confirmation link, then return here to sign in."
                        }
                    }
                }, onSecondary = { go("login") }, secondary = "Already have an account? Sign in")
                "forgot" -> ForgotScreen(busy, onBack = { go("login") }, onSubmit = { email ->
                    run { backend.recover(email); message = "If the address is registered, recovery instructions will be sent. Open the link on this device to set a new password." }
                })
                "resetPassword" -> ResetScreen(busy, onSubmit = { password ->
                    run { backend.updatePassword(password); profile = null; screen = "login"; message = "Password updated. Please sign in with your new password." }
                })
                "home" -> {
                    Text("YOUR LEARNING SPACE", style = MaterialTheme.typography.labelMedium, color = Teal, fontWeight = FontWeight.Bold)
                    Text(if (demo) "Welcome, student" else "Welcome${profile?.name?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.headlineMedium, color = Navy, fontWeight = FontWeight.Bold)
                    Text("Pick up where your learning begins.", color = Color(0xFF536477))
                    HubCard("Explore qualifications", "Find your programme and browse its modules.", "Browse qualifications", ::openQualifications)
                    HubCard("Your profile", "View your account information and role.", "View profile", { go("profile") })
                    if (!demo && profile?.role == "admin") HubCard("Administrator", "Access the administration foundation.", "Open dashboard", { go("admin") })
                    Text("Documents are listed where published. Downloads will be added later.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                "qualifications" -> {
                    PageHeader("Qualifications", "Select a qualification to view its modules.")
                    if (!busy && qualifications.isEmpty()) Text("No qualifications are available yet.")
                    qualifications.forEach { q -> HubCard(q.title + if (!q.published) " · Draft" else "", q.description, "View modules") { openModules(q) } }
                    OutlinedButton(onClick = { go("home") }) { Text("Back to dashboard") }
                }
                "modules" -> {
                    PageHeader(qualification?.title ?: "Modules", "Modules in this qualification")
                    if (!busy && modules.isEmpty()) Text("No modules are available yet.")
                    modules.forEach { m -> HubCard(m.title + if (!m.published) " · Draft" else "", listOf(m.code, m.description).filter { it.isNotBlank() }.joinToString(" · "), "View module") { selectedModule = m; go("moduleDetail"); documents = emptyList(); if (!demo) run { documents = backend.documents(m.id) } } }
                    OutlinedButton(onClick = { go("qualifications") }) { Text("Back to qualifications") }
                }
                "moduleDetail" -> {
                    PageHeader(selectedModule?.title ?: "Module", selectedModule?.code.orEmpty())
                    Text(selectedModule?.description.orEmpty())
                    Text("Published documents", style = MaterialTheme.typography.titleLarge)
                    if (!busy && documents.isEmpty()) Text("No documents are available yet.")
                    documents.forEach { doc -> Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(doc.title, style=MaterialTheme.typography.titleMedium); Text(doc.description); if (!doc.published) Text("Draft"); Text("File access is not connected yet.", style=MaterialTheme.typography.bodySmall) } } }
                    OutlinedButton(onClick = { go("modules") }) { Text("Back to modules") }
                }
                "profile" -> {
                    PageHeader("Your profile", "Your account details")
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (demo) "Sample student" else profile?.name.orEmpty().ifBlank { "Student" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(if (demo) "Sample profile · not signed in" else profile?.email.orEmpty())
                            Text(if (demo) "Role: sample student" else "Role: ${profile?.role ?: "unknown"}", color = Teal)
                        }
                    }
                    OutlinedButton(onClick = { go("home") }) { Text("Back to dashboard") }
                    TextButton(enabled = !busy, onClick = { if (demo) { demo = false; profile = null; go("welcome") } else run { backend.logout(); profile = null; screen = "welcome" } }) { Text(if (demo) "Exit sample mode" else "Sign out") }
                }
                "admin" -> if (!demo && profile?.role == "admin") { AdminPanel(backend) { go("home") } } else { Text("Administrator access required.") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).background(Navy, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("S", color = Color(0xFF70D7C3), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        Column { Text("STUDENT MODULE HUB", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.labelLarge); Text("Learn with purpose", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
    }
}
@Composable fun Notice(text: String, error: Boolean) {
    Surface(color = if (error) Color(0xFFFFEDEA) else Color(0xFFE4F2EF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text(text, modifier = Modifier.padding(14.dp), color = if (error) Color(0xFF8C3127) else Color(0xFF225F56), style = MaterialTheme.typography.bodyMedium) }
}
@Composable fun PageHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    if (subtitle.isNotBlank()) Text(subtitle, color = Color(0xFF536477))
}
@Composable fun HubCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Navy)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF536477))
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text("$action  →") }
        }
    }
}
@Composable fun AuthScreen(title: String, subtitle: String, action: String, busy: Boolean, register: Boolean, onBack: () -> Unit, onSubmit: (String, String, String) -> Unit, onSecondary: () -> Unit, secondary: String) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf("") }

    TextButton(onClick = onBack, enabled = !busy) { Text("← Back") }
    Brand()
    Spacer(Modifier.height(10.dp))
    PageHeader(title, subtitle)
    if (validation.isNotBlank()) Notice(validation, true)

    if (register) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; validation = "" },
            label = { Text("Full name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy
        )
    }
    OutlinedTextField(
        value = email,
        onValueChange = { email = it; validation = "" },
        label = { Text("Email address") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it; validation = "" },
        label = { Text(if (register) "Create password" else "Password") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !busy,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !busy) {
                Text(if (passwordVisible) "Hide" else "Show")
            }
        }
    )
    if (register) {
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; validation = "" },
            label = { Text("Confirm password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { confirmVisible = !confirmVisible }, enabled = !busy) {
                    Text(if (confirmVisible) "Hide" else "Show")
                }
            }
        )
    }
    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
        validation = Validation.auth(name, email, password, register, confirmPassword)
        if (validation.isEmpty()) onSubmit(name.trim(), email.trim().lowercase(), password)
    }) { Text(action) }
    TextButton(enabled = !busy, onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
}
@Composable fun ForgotScreen(busy: Boolean, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf("") }; var validation by remember { mutableStateOf("") }
    TextButton(onClick = onBack, enabled = !busy) { Text("← Back") }; Brand(); Spacer(Modifier.height(10.dp)); PageHeader("Forgot password?", "Enter your email to request recovery instructions.")
    if (validation.isNotEmpty()) Notice(validation, true)
    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { if (!email.contains("@")) validation = "Enter a valid email address." else { validation = ""; onSubmit(email) } }) { Text("Send recovery email") }
}

@Composable fun ResetScreen(busy: Boolean, onSubmit: (String) -> Unit) {
    var pass by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var warning by remember { mutableStateOf("") }
    Brand(); Spacer(Modifier.height(10.dp)); PageHeader("Set a new password", "Choose a password for your account.")
    if (warning.isNotEmpty()) Notice(warning, true)
    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("New password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { warning = when { pass.length < 8 -> "Use at least 8 characters."; pass != confirm -> "Passwords do not match."; else -> "" }; if (warning.isEmpty()) onSubmit(pass) }) { Text("Update password") }
}
