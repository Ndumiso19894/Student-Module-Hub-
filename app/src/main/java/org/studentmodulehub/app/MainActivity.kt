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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.launch

private val Navy = Color(0xFF172554)
private val Blue = Color(0xFF2563EB)
private val Teal = Color(0xFF14B8A6)
private val Pale = Color(0xFFF4F7FC)
private val Muted = Color(0xFF64748B)
private val scheme = lightColorScheme(primary = Blue, secondary = Navy, tertiary = Teal, background = Pale, surface = Color.White, onPrimary = Color.White)

class MainActivity : ComponentActivity() {
    private var callback by mutableStateOf<Pair<String, String>?>(null)
    private fun receive(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "org.studentmodulehub.app" && uri.host == "auth" && uri.path in listOf("/confirm", "/recovery", "/google")) {
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
    var selectedCollege by remember { mutableStateOf<College?>(null) }
    var colleges by remember { mutableStateOf<List<College>>(emptyList()) }
    var catalogueSearch by remember { mutableStateOf("") }
    var qualifications by remember { mutableStateOf<List<Qualification>>(emptyList()) }
    var modules by remember { mutableStateOf<List<Module>>(emptyList()) }
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchColleges by remember { mutableStateOf<List<College>>(emptyList()) }
    var searchQualifications by remember { mutableStateOf<List<Qualification>>(emptyList()) }
    var searchModules by remember { mutableStateOf<List<Module>>(emptyList()) }
    var localDownloads by remember { mutableStateOf<List<File>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun clearMessage() { message = "" }
    fun go(value: String) { clearMessage(); screen = value }
    fun errorMessage(e: Exception): String {
        val detail = generateSequence<Throwable>(e) { it.cause }
            .take(3)
            .joinToString(" → ") { cause ->
                val type = cause::class.simpleName ?: "Error"
                val description = cause.message?.replace(Regex("\\s+"), " ")?.take(180).orEmpty()
                if (description.isBlank()) type else "$type: $description"
            }
        val summary = when (e) {
            is BackendException -> when (e.code) {
                400, 401 -> "The request was rejected. Check the account details and Supabase settings."
                403 -> "Supabase denied access. Check the account permissions and database policies."
                429 -> "Too many requests. Wait a moment and try again."
                else -> "Supabase returned HTTP ${e.code}."
            }
            is java.net.UnknownHostException -> "The phone could not find the Supabase server. Check the URL and internet connection."
            is java.net.ConnectException -> "The phone could not connect to the Supabase server."
            is java.net.SocketTimeoutException -> "The Supabase request timed out."
            is java.io.IOException -> "A network request failed."
            else -> "The operation failed."
        }
        return "$summary\\nTechnical detail: ${detail.ifBlank { "No additional error details were provided." }}"
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; clearMessage()
        scope.launch { try { action() } catch (e: Exception) { message = errorMessage(e) } finally { busy = false } }
    }
    fun openQualifications() {
        go("colleges"); catalogueSearch = ""; colleges = emptyList()
        if (demo) colleges = listOf(College("SAMPLE", "Sample College"))
        else run { colleges = backend.colleges() }
    }
    fun openCollege(college: College) {
        selectedCollege = college; go("qualifications"); catalogueSearch = ""; qualifications = emptyList()
        if (demo) qualifications = listOf(Qualification("sample", "Diploma in Business Studies", "Sample qualification for navigation testing.", true, college.id))
        else run { qualifications = backend.qualifications(college.id) }
    }
    fun openModules(q: Qualification) {
        qualification = q; go("modules"); modules = emptyList()
        if (demo) modules = listOf(Module("sample-1", "Introduction to Management", "BUS101", "Sample module for navigation testing.", true, qualificationId = q.id), Module("sample-2", "Business Communication", "BUS102", "Sample module for navigation testing.", true, qualificationId = q.id))
        else run { modules = backend.modules(q.id) }
    }
    fun openSearch() {
        go("search"); searchQuery = ""
        if (demo) {
            searchColleges = listOf(College("SAMPLE", "Sample College"))
            searchQualifications = listOf(Qualification("sample", "Diploma in Business Studies", "Sample qualification", true, "SAMPLE"))
            searchModules = listOf(Module("sample-1", "Introduction to Management", "BUS101", "Sample module", true, qualificationId = "sample"))
        } else {
            searchColleges = emptyList(); searchQualifications = emptyList(); searchModules = emptyList()
            run { searchColleges = backend.colleges(); searchQualifications = backend.qualifications(); searchModules = backend.allModules() }
        }
    }
    LaunchedEffect(Unit) {
        if (backend.configured && callback == null) {
            busy = true
            try { backend.restore()?.let { profile = it; screen = "home" } } catch (e: Exception) { message = "Could not restore your session. Please sign in.\\n${errorMessage(e)}" }
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
                    demo = false
                    screen = "home"
                    message = if (incoming.first == "/google") "Google sign-in successful." else "Email confirmed. You are signed in."
                } else {
                    profile = null
                    screen = "login"
                    message = if (incoming.first == "/google") "Google sign-in completed. Please sign in to continue." else "Email confirmed. Please sign in to continue."
                }
            }
        } catch (e: Exception) { screen = "login"; message = "The authentication link could not be verified.\\n${errorMessage(e)}" }
        finally { busy = false; clearCallback() }
    }
    BackHandler(enabled = screen != "welcome" && !busy) {
        when (screen) {
            "login", "register", "forgot" -> go("welcome")
            "resetPassword" -> { run { backend.logout(); profile = null; screen = "login" } }
            "modules" -> go("qualifications")
            "moduleDetail" -> go("modules")
            "qualifications" -> go("colleges")
            "colleges", "profile", "admin", "search", "downloads" -> go("home")
            "home" -> if (demo) { demo = false; go("welcome") } else go("profile")
            else -> go("welcome")
        }
    }
    var selectedModule by remember { mutableStateOf<Module?>(null) }
    val context = LocalContext.current
    fun openDownloads() {
        go("downloads")
        val folder = File(context.cacheDir, "study-materials")
        localDownloads = folder.listFiles()?.filter { it.isFile && it.extension.equals("pdf", true) }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    Scaffold(containerColor = Pale, topBar = {
        if (screen !in listOf("welcome", "login", "register", "forgot", "resetPassword")) {
            CenterAlignedTopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(30.dp).background(Navy, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text("S", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text("Student Module Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
                } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                actions = { TextButton(onClick = ::openSearch) { Text("Search") } }
            )
        }
    }, bottomBar = {
        if (screen in listOf("home", "colleges", "qualifications", "modules", "moduleDetail", "search", "downloads", "profile", "admin")) {
            NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
                NavigationBarItem(selected = screen == "home", onClick = { go("home") }, icon = { Text("⌂", style = MaterialTheme.typography.titleLarge) }, label = { Text("Home") })
                NavigationBarItem(selected = screen in listOf("colleges", "qualifications", "modules", "moduleDetail"), onClick = ::openQualifications, icon = { Text("▦", style = MaterialTheme.typography.titleLarge) }, label = { Text("Browse") })
                NavigationBarItem(selected = screen == "search", onClick = ::openSearch, icon = { Text("⌕", style = MaterialTheme.typography.titleLarge) }, label = { Text("Search") })
                NavigationBarItem(selected = screen == "downloads", onClick = ::openDownloads, icon = { Text("↓", style = MaterialTheme.typography.titleLarge) }, label = { Text("Files") })
                NavigationBarItem(selected = screen == "profile" || screen == "admin", onClick = { go("profile") }, icon = { Text("●", style = MaterialTheme.typography.titleLarge) }, label = { Text("Profile") })
            }
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
                    OutlinedButton(onClick = {
                        if (!backend.configured) message = "Google sign-in is not configured yet."
                        else run { backend.signInWithGoogle() }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Continue with Google") }
                    TextButton(onClick = { demo = true; profile = null; go("home") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Explore sample screens") }
                    Text("Sample mode is for interface testing only. No account is created or signed in.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                "login" -> AuthScreen("Welcome back", "Sign in to continue your studies.", "Sign in", busy, false, onBack = { go("welcome") }, onSubmit = { _, email, pass ->
                    run { profile = backend.login(email, pass); demo = false; screen = "home" }
                }, onGoogle = {
                    if (!backend.configured) message = "Google sign-in is not configured yet."
                    else run { backend.signInWithGoogle() }
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
                }, onGoogle = {
                    if (!backend.configured) message = "Google sign-in is not configured yet."
                    else run { backend.signInWithGoogle() }
                }, onSecondary = { go("login") }, secondary = "Already have an account? Sign in")
                "forgot" -> ForgotScreen(busy, onBack = { go("login") }, onSubmit = { email ->
                    run { backend.recover(email); message = "If the address is registered, recovery instructions will be sent. Open the link on this device to set a new password." }
                })
                "resetPassword" -> ResetScreen(busy, onSubmit = { password ->
                    run { backend.updatePassword(password); profile = null; screen = "login"; message = "Password updated. Please sign in with your new password." }
                })
                "home" -> {
                    Text("YOUR LEARNING SPACE", style = MaterialTheme.typography.labelMedium, color = Blue, fontWeight = FontWeight.Bold)
                    Text(if (demo) "Welcome, student" else "Welcome back", style = MaterialTheme.typography.headlineMedium, color = Navy, fontWeight = FontWeight.Bold)
                    Text("Everything you need to keep your studies moving.", color = Muted)
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("FIND YOUR NEXT RESOURCE", color = Color(0xFFB8D1FF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Search the UNISA catalogue", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Look up colleges, qualifications, module codes and study materials.", color = Color(0xFFE2E8F0))
                            Button(onClick = ::openSearch, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("⌕  Search catalogue") }
                        }
                    }
                    Text("QUICK ACCESS", color = Blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        HomeTile("Browse colleges", "Explore all colleges", "▦", ::openQualifications, Modifier.weight(1f))
                        HomeTile("My profile", "Account details", "●", { go("profile") }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        HomeTile("My files", "Recently opened PDFs", "↓", ::openDownloads, Modifier.weight(1f))
                        HomeTile("Search", "Find a module", "⌕", ::openSearch, Modifier.weight(1f))
                    }
                    if (!demo && profile?.role == "admin") HubCard("Administrator tools", "Manage catalogue records and study resources.", "Open admin dashboard", { go("admin") })
                    Text("Browse from college to qualification, module and its study materials.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                "colleges" -> {
                    PageHeader("UNISA colleges", "Choose a college to browse its qualifications.")
                    OutlinedTextField(value=catalogueSearch, onValueChange={ catalogueSearch=it }, label={ Text("Search colleges") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
                    val visibleColleges = colleges.filter { it.name.contains(catalogueSearch, ignoreCase=true) || it.id.contains(catalogueSearch, ignoreCase=true) }
                    if (!busy && visibleColleges.isEmpty()) Text("No colleges found.")
                    visibleColleges.forEach { college -> HubCard(college.name, "Browse qualifications offered by this college.", "View qualifications") { openCollege(college) } }
                    OutlinedButton(onClick = { go("home") }) { Text("Back to dashboard") }
                }
                "qualifications" -> {
                    PageHeader(selectedCollege?.name ?: "Qualifications", "Select a qualification to view its modules.")
                    OutlinedTextField(value=catalogueSearch, onValueChange={ catalogueSearch=it }, label={ Text("Search qualifications or code") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
                    val visibleQualifications = qualifications.filter { it.title.contains(catalogueSearch, ignoreCase=true) || it.qualificationCode.contains(catalogueSearch, ignoreCase=true) }
                    if (!busy && visibleQualifications.isEmpty()) Text("No qualifications found for this college.")
                    visibleQualifications.forEach { q ->
                        val details = listOf(q.qualificationCode, q.level, q.academicYear?.let { "Academic year $it" }.orEmpty(), q.description).filter { it.isNotBlank() }.joinToString(" · ")
                        HubCard(q.title + if (!q.published) " · Draft" else "", details, "View modules") { openModules(q) }
                    }
                    OutlinedButton(onClick = { go("colleges") }) { Text("Back to colleges") }
                }
                "modules" -> {
                    PageHeader(qualification?.title ?: "Modules", "Modules in this qualification")
                    if (!busy && modules.isEmpty()) Text("No modules are available yet.")
                    OutlinedTextField(value=catalogueSearch, onValueChange={ catalogueSearch=it }, label={ Text("Search modules or module code") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
                    modules.filter { it.title.contains(catalogueSearch, ignoreCase=true) || it.code.contains(catalogueSearch, ignoreCase=true) }.forEach { m -> HubCard(m.title + if (!m.published) " · Draft" else "", listOf(m.code, m.credits?.let { "$it credits" }.orEmpty(), m.nqfLevel?.let { "NQF $it" }.orEmpty(), m.semester.takeIf { it.isNotBlank() }?.let { "Semester $it" }.orEmpty(), m.academicYear?.let { "Academic year $it" }.orEmpty(), m.description).filter { it.isNotBlank() }.joinToString(" · "), "View module") { selectedModule = m; go("moduleDetail"); documents = emptyList(); if (!demo) run { documents = backend.documents(m.id) } } }
                    OutlinedButton(onClick = { go("qualifications") }) { Text("Back to qualifications") }
                }
                "moduleDetail" -> {
                    PageHeader(selectedModule?.title ?: "Module", selectedModule?.code.orEmpty())
                    Text(selectedModule?.description.orEmpty())
                    Text("Published documents", style = MaterialTheme.typography.titleLarge)
                    if (!busy && documents.isEmpty()) Text("No documents are available yet.")
                    MaterialCategories.forEach { category ->
                        val items = documents.filter { it.category == category }
                        if (items.isNotEmpty()) {
                            Text(category, style=MaterialTheme.typography.titleMedium, color=Teal)
                            items.forEach { doc -> Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(doc.title, style=MaterialTheme.typography.titleMedium); val academicDetails = listOf(doc.academicYear?.let { "Academic year $it" }.orEmpty(), doc.semester.takeIf { it.isNotBlank() }?.let { "Semester $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "); if (academicDetails.isNotBlank()) Text(academicDetails, style=MaterialTheme.typography.bodySmall, color=Color.Gray); if (doc.description.isNotBlank()) Text(doc.description); Button(enabled=!busy && !demo, onClick={
                                run {
                                    val folder = File(context.cacheDir, "study-materials").apply { mkdirs() }
                                    val safeName = doc.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90).ifBlank { "study-material" } + ".pdf"
                                    val file = File(folder, safeName)
                                    backend.downloadMaterial(doc.id, file)
                                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }) { Text("Open PDF") } } } }
                        }
                    }
                    OutlinedButton(onClick = { go("modules") }) { Text("Back to modules") }
                }
                "search" -> {
                    PageHeader("Search the catalogue", "Search colleges, qualifications and module codes in one place.")
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("What are you looking for?") }, leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge, color = Blue) })
                    if (busy && searchColleges.isEmpty()) Text("Loading catalogue...")
                    if (searchQuery.isBlank()) Text("Start typing a college, qualification name or module code.", color = Muted)
                    else {
                        val term = searchQuery.trim()
                        val foundColleges = searchColleges.filter { it.name.contains(term, true) || it.id.contains(term, true) }
                        val foundQualifications = searchQualifications.filter { it.title.contains(term, true) || it.qualificationCode.contains(term, true) }
                        val foundModules = searchModules.filter { it.title.contains(term, true) || it.code.contains(term, true) }.take(30)
                        if (foundColleges.isEmpty() && foundQualifications.isEmpty() && foundModules.isEmpty() && !busy) Text("No matches found. Try a different name or module code.")
                        if (foundColleges.isNotEmpty()) {
                            Text("COLLEGES", color = Blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            foundColleges.forEach { c -> HubCard(c.name, "Browse qualifications in this college.", "View college") { openCollege(c) } }
                        }
                        if (foundQualifications.isNotEmpty()) {
                            Text("QUALIFICATIONS", color = Blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            foundQualifications.take(25).forEach { qf -> HubCard(qf.title, listOf(qf.qualificationCode, qf.level, qf.academicYear?.toString().orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), "View modules") { openModules(qf) } }
                        }
                        if (foundModules.isNotEmpty()) {
                            Text("MODULES", color = Blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            foundModules.forEach { mod ->
                                val parent = searchQualifications.firstOrNull { it.id == mod.qualificationId }
                                HubCard(mod.title, listOf(mod.code, parent?.title.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), "Open module") {
                                    if (parent != null) {
                                        qualification = parent; selectedModule = mod; go("moduleDetail"); documents = emptyList()
                                        if (!demo) run { documents = backend.documents(mod.id) }
                                    }
                                }
                            }
                        }
                    }
                }
                "downloads" -> {
                    PageHeader("My files", "PDFs recently opened from your study materials.")
                    if (localDownloads.isEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("No recent PDFs", style = MaterialTheme.typography.titleMedium, color = Navy, fontWeight = FontWeight.Bold)
                                Text("When you open a study document, it will appear here while it remains in temporary app storage.", color = Muted)
                            }
                        }
                    } else localDownloads.forEach { file ->
                        HubCard(file.name, "PDF · Recently opened", "Open PDF") {
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    }
                    Text("These are temporary cached files, not permanent offline downloads.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                "profile" -> {
                    PageHeader("My profile", "Manage your Student Module Hub account.")
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(78.dp).background(Color(0xFFE8EEFF), CircleShape), contentAlignment = Alignment.Center) {
                                Text(if (demo) "S" else profile?.name?.firstOrNull()?.uppercase() ?: "S", color = Blue, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            }
                            Text(if (demo) "Sample student" else profile?.name.orEmpty().ifBlank { "Student" }, style = MaterialTheme.typography.titleLarge, color = Navy, fontWeight = FontWeight.Bold)
                            Text(if (demo) "Sample profile · not signed in" else profile?.email.orEmpty(), color = Muted)
                            Surface(color = Color(0xFFE8F5F2), shape = RoundedCornerShape(50)) {
                                Text(if (demo) "SAMPLE ACCOUNT" else (profile?.role ?: "STUDENT").uppercase(), color = Color(0xFF087A68), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                        }
                    }
                    Text("ACCOUNT", color = Blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    HubCard("My qualifications", "Browse qualifications and their modules.", "Browse catalogue", ::openQualifications)
                    HubCard("My recent files", "View PDFs opened from study materials.", "View files", ::openDownloads)
                    if (!demo && profile?.role == "admin") HubCard("Administrator tools", "Manage catalogue records and study resources.", "Open admin", { go("admin") })
                    Text("Account email", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(if (demo) "Not signed in" else profile?.email.orEmpty(), color = Navy)
                    TextButton(enabled = !busy, onClick = { if (demo) { demo = false; profile = null; go("welcome") } else run { backend.logout(); profile = null; screen = "welcome" } }) { Text("Sign out") }
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Navy)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Text(action + "  →", color = Blue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable fun HomeTile(title: String, subtitle: String, symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(40.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text(symbol, color = Blue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(title, color = Navy, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable fun AuthScreen(title: String, subtitle: String, action: String, busy: Boolean, register: Boolean, onBack: () -> Unit, onSubmit: (String, String, String) -> Unit, onGoogle: () -> Unit, onSecondary: () -> Unit, secondary: String) {
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
        if (validation.isEmpty()) onSubmit(name.trim(), email.trim(), password)
    }) { Text(action) }
    Text("OR", modifier = Modifier.fillMaxWidth(), color = Color.Gray, style = MaterialTheme.typography.labelMedium)
    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = onGoogle) { Text("Continue with Google") }
    TextButton(enabled = !busy, onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
}
@Composable fun ForgotScreen(busy: Boolean, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf("") }; var validation by remember { mutableStateOf("") }
    TextButton(onClick = onBack, enabled = !busy) { Text("← Back") }; Brand(); Spacer(Modifier.height(10.dp)); PageHeader("Forgot password?", "Enter your email to request recovery instructions.")
    if (validation.isNotEmpty()) Notice(validation, true)
    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { if (!Validation.email(email)) validation = "Enter a valid email address." else { validation = ""; onSubmit(email.trim()) } }) { Text("Send recovery email") }
}

@Composable fun ResetScreen(busy: Boolean, onSubmit: (String) -> Unit) {
    var pass by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var warning by remember { mutableStateOf("") }
    Brand(); Spacer(Modifier.height(10.dp)); PageHeader("Set a new password", "Choose a password for your account.")
    if (warning.isNotEmpty()) Notice(warning, true)
    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("New password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { warning = when { pass.length < 8 -> "Use at least 8 characters."; pass != confirm -> "Passwords do not match."; else -> "" }; if (warning.isEmpty()) onSubmit(pass) }) { Text("Update password") }
}
