package org.studentmodulehub.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Navigation is convenience only. All requests remain subject to database RLS. */
@Composable
fun AdminPanel(backend: Backend, onExit: () -> Unit) {
    var section by remember { mutableStateOf("menu") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var qs by remember { mutableStateOf<List<Qualification>>(emptyList()) }
    var ms by remember { mutableStateOf<List<Module>>(emptyList()) }
    var ds by remember { mutableStateOf<List<Document>>(emptyList()) }
    var students by remember { mutableStateOf<List<StudentAccount>>(emptyList()) }
    var q by remember { mutableStateOf<Qualification?>(null) }
    var m by remember { mutableStateOf<Module?>(null) }
    var table by remember { mutableStateOf("") }
    var itemId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var published by remember { mutableStateOf(false) }
    var parentId by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Pair<String,String>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var uploadTarget by remember { mutableStateOf<String?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadTarget
        if (uri != null && target != null) job {
            backend.uploadDocumentPdf(target, uri, context.contentResolver)
            ds = backend.documents(m!!.id)
            notice = "PDF uploaded successfully."
        }
        uploadTarget = null
    }
    fun job(block: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = ""
        scope.launch { try { block() } catch (_: Exception) { notice = "Action failed. Check permissions and connection. A qualification or module with dependent records cannot be deleted." } finally { busy = false } }
    }
    fun loadQualifications() { section = "qualifications"; job { qs = backend.qualifications() } }
    fun loadModules(qualification: Qualification) { q = qualification; section = "modules"; job { ms = backend.modules(qualification.id) } }
    fun loadDocuments(module: Module) { m = module; section = "documents"; job { ds = backend.documents(module.id) } }
    fun edit(t: String, id: String?, name: String, desc: String, c: String, live: Boolean, parent: String) {
        table=t; itemId=id; title=name; description=desc; code=c; published=live; parentId=parent; notice=""; section="edit"
    }
    PageHeader("Administrator", "Catalogue and student management")
    if (notice.isNotBlank()) Notice(notice, true)
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    when(section) {
        "menu" -> {
            HubCard("Students", "View registered profiles and roles.", "View students") { section="students"; job { students = backend.students() } }
            HubCard("Qualifications & modules", "Create and maintain the catalogue and document metadata.", "Manage catalogue") { loadQualifications() }
            OutlinedButton(onClick=onExit) { Text("Back to dashboard") }
        }
        "students" -> {
            Text("Student accounts", style=MaterialTheme.typography.titleLarge)
            Text("Read-only: role changes and account suspension are not available here.")
            if (!busy && students.isEmpty()) Text("No registered profiles found.")
            students.forEach { user -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(user.name.ifBlank { "Unnamed account" }); Text("Role: ${user.role}"); Text("ID: ${user.id}", style=MaterialTheme.typography.bodySmall); Text("Created: ${user.createdAt.take(10)}", style=MaterialTheme.typography.bodySmall) } } }
            OutlinedButton(onClick={ section="menu" }) { Text("Back") }
        }
        "qualifications" -> {
            Text("Qualifications", style=MaterialTheme.typography.titleLarge)
            Button(enabled=!busy, onClick={ edit("qualifications", null, "", "", "", false, "") }) { Text("Add qualification") }
            if (!busy && qs.isEmpty()) Text("No qualifications yet.")
            qs.forEach { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(item.title, style=MaterialTheme.typography.titleMedium); Text(if(item.published) "Published" else "Draft")
                Row { TextButton(enabled=!busy, onClick={ edit("qualifications", item.id, item.title, item.description, "", item.published, "") }) { Text("Edit") }; TextButton(enabled=!busy, onClick={ loadModules(item) }) { Text("Modules") }; TextButton(enabled=!busy, onClick={ pendingDelete="qualifications" to item.id }) { Text("Delete") } }
            } } }
            OutlinedButton(onClick={ section="menu" }) { Text("Back") }
        }
        "modules" -> {
            Text("Modules · ${q?.title}", style=MaterialTheme.typography.titleLarge)
            Button(enabled=!busy, onClick={ edit("modules", null, "", "", "", false, q!!.id) }) { Text("Add module") }
            if (!busy && ms.isEmpty()) Text("No modules yet.")
            ms.forEach { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(item.title, style=MaterialTheme.typography.titleMedium); Text("${item.code} · ${if(item.published) "Published" else "Draft"}")
                Row { TextButton(enabled=!busy, onClick={ edit("modules", item.id, item.title, item.description, item.code, item.published, q!!.id) }) { Text("Edit") }; TextButton(enabled=!busy, onClick={ loadDocuments(item) }) { Text("Documents") }; TextButton(enabled=!busy, onClick={ pendingDelete="modules" to item.id }) { Text("Delete") } }
            } } }
            OutlinedButton(onClick={ loadQualifications() }) { Text("Back") }
        }
        "documents" -> {
            Text("Documents · ${m?.title}", style=MaterialTheme.typography.titleLarge)
            Text("Upload a PDF for each document. Files are stored privately; students can download them only when the document and its parent records are published.")
            Button(enabled=!busy, onClick={ edit("documents", null, "", "", "", false, m!!.id) }) { Text("Add document") }
            if (!busy && ds.isEmpty()) Text("No documents yet.")
            ds.forEach { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(item.title, style=MaterialTheme.typography.titleMedium)
                Text(if(item.published) "Published" else "Draft")
                Text(if(item.storagePath.isNotBlank()) "PDF uploaded" else "No PDF attached", style=MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled=!busy, onClick={ edit("documents", item.id, item.title, item.description, "", item.published, m!!.id) }) { Text("Edit") }
                    TextButton(enabled=!busy, onClick={ uploadTarget=item.id; pdfPicker.launch(arrayOf("application/pdf")) }) { Text(if(item.storagePath.isBlank()) "Upload PDF" else "Replace PDF") }
                    TextButton(enabled=!busy, onClick={ pendingDelete="documents" to item.id }) { Text("Delete") }
                }
            } } }
            OutlinedButton(onClick={ loadModules(q!!) }) { Text("Back") }
        }
        "edit" -> {
            Text(if(itemId==null) "Create ${table.dropLast(1)}" else "Edit ${table.dropLast(1)}", style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(value=title, onValueChange={ title=it }, label={ Text("Title") }, modifier=Modifier.fillMaxWidth())
            if (table=="modules") OutlinedTextField(value=code, onValueChange={ code=it }, label={ Text("Module code") }, modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=description, onValueChange={ description=it }, label={ Text("Description") }, modifier=Modifier.fillMaxWidth())
            if (table=="modules") {
                Text("Assign qualification:")
                qs.forEach { choice -> Row { RadioButton(selected=parentId==choice.id, onClick={parentId=choice.id}); Text(choice.title, modifier=Modifier.padding(top=14.dp)) } }
            }
            if (table=="documents") {
                Text("Assign module in ${q?.title}:")
                ms.forEach { choice -> Row { RadioButton(selected=parentId==choice.id, onClick={parentId=choice.id}); Text(choice.title, modifier=Modifier.padding(top=14.dp)) } }
            }
            Row { Checkbox(checked=published, onCheckedChange={ published=it }); Text("Published", modifier=Modifier.padding(top=14.dp)) }
            Button(enabled=!busy, onClick={
                if(title.isBlank() || (table!="qualifications" && parentId.isBlank())) notice="Enter a title and select the parent record."
                else job {
                    backend.saveItem(table, itemId, title, description, code, parentId.takeIf { it.isNotBlank() }, published)
                    when(table) { "qualifications" -> { qs=backend.qualifications(); section="qualifications" }; "modules" -> { ms=backend.modules(q!!.id); section="modules" }; else -> { ds=backend.documents(m!!.id); section="documents" } }
                    notice="Saved."
                }
            }) { Text("Save") }
            OutlinedButton(enabled=!busy, onClick={ section=when(table) { "qualifications" -> "qualifications"; "modules" -> "modules"; else -> "documents" } }) { Text("Cancel") }
        }
    }
    pendingDelete?.let { (t,id) ->
        AlertDialog(onDismissRequest={ pendingDelete=null }, title={ Text("Delete this record?") }, text={ Text("This cannot be undone. Records with dependent modules or documents cannot be deleted.") }, confirmButton={ TextButton(enabled=!busy, onClick={ pendingDelete=null; job { backend.deleteItem(t,id); when(t) { "qualifications" -> qs=backend.qualifications(); "modules" -> ms=backend.modules(q!!.id); else -> ds=backend.documents(m!!.id) }; notice="Deleted." } }) { Text("Delete") } }, dismissButton={ TextButton(onClick={ pendingDelete=null }) { Text("Cancel") } })
    }
}