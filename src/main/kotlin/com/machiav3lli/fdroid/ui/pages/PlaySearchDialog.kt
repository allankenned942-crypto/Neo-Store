package com.machiav3lli.fdroid.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.machiav3lli.fdroid.data.index.AuroraSearch
import kotlinx.coroutines.launch

@Composable
fun PlaySearchDialog(
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AuroraSearch.Hit>>(emptyList()) }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        scope.launch {
            loading = true
            error = ""
            try {
                results = AuroraSearch.search(context, q)
                if (results.isEmpty()) error = "Nada encontrado"
            } catch (e: Exception) {
                results = emptyList()
                error = e.message ?: e.toString()
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar na Google Play") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                )
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { doSearch() },
                        enabled = !loading && query.isNotBlank(),
                    ) { Text("Buscar") }
                    if (loading) CircularProgressIndicator(
                        modifier = Modifier.padding(start = 12.dp).size(24.dp)
                    )
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.pkg }) { r ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(r.pkg, r.name)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge)
                            Text(r.pkg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
