package lol.omg.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Lilac = Color(0xFF6853A0)
@Composable fun PocketApp(model: AccountViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = lightColorScheme(primary = Lilac, secondary = Color(0xFF4E7068),
        background = Color(0xFFF9F7FC), surface = Color(0xFFF9F7FC))) {
        Scaffold { insets ->
            Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, color = Lilac) {
                        Text(":)", Modifier.padding(14.dp), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("omg.lol pocket", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("A little home for your addresses.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (state.restoring) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Checking saved sign-in…")
                } else if (state.account == null) {
                    SignIn(state, model::signIn, model::demo, model::refresh)
                    if (state.busy) TextButton(onClick = model::signOut) { Text("Cancel") }
                } else {
                    AccountScreen(state, model::refresh, model::selectAddress, model::signOut)
                }
                Text("Independent omg.lol client · Read-only preview", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun SignIn(state: AccountUiState, signIn: (String, String, Boolean) -> Unit, demo: () -> Unit, retry: () -> Unit) {
    // remember rather than rememberSaveable: API keys must not enter saved instance state.
    var email by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var rememberSignIn by remember { mutableStateOf(false) }
    Text("Your corner of the internet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("See your account and addresses in one quiet little place.")
    state.notice?.let { MessageCard(it) }
    state.error?.let { MessageCard(it, error = true) }
    if (state.canRetry) OutlinedButton(onClick = retry, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Retry connection") }
    OutlinedTextField(email, { email = it }, label = { Text("Account email") }, singleLine = true,
        enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true,
        enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(rememberSignIn, { rememberSignIn = it }, enabled = !state.busy)
        Text("Remember on this device")
    }
    Text("Use the API key from your omg.lol account settings. Remembering sign-in stores it encrypted on this device. Your key may grant more access than this read-only app uses.",
        style = MaterialTheme.typography.bodySmall)
    Button(onClick = { signIn(email, key, rememberSignIn); key = "" }, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if (state.busy) "Connecting…" else "Connect account") }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    OutlinedButton(onClick = { email = ""; key = ""; demo() }, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Try demo") }
}

@Composable private fun AccountScreen(state: AccountUiState, refresh: () -> Unit, select: (String) -> Unit, signOut: () -> Unit) {
    if (state.demo) MessageCard("Demo mode · Sample data, no account connected.")
    state.notice?.let { MessageCard(it) }
    state.error?.let { MessageCard(it, error = true) }
    val account = state.account ?: return
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFECE5F7)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR ACCOUNT", style = MaterialTheme.typography.labelMedium, color = Lilac)
            Text(account.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(account.email)
            Text("Joined ${account.created}", style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = refresh, enabled = !state.busy) { Text(if (state.busy) "Refreshing…" else "Refresh") }
        TextButton(onClick = signOut) { Text(if (state.demo) "Exit demo" else "Sign out") }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text("Your addresses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (state.addresses.isEmpty()) MessageCard("No addresses belong to this account yet.")
    state.addresses.forEach { address ->
        OutlinedCard(onClick = { select(address.name) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = if (address.name == state.selected) Color(0xFFEAF2EE) else MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${address.name}.omg.lol", fontWeight = FontWeight.SemiBold)
                Text(address.expiration, style = MaterialTheme.typography.bodySmall)
                if (address.name == state.selected) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
    if (state.selected != null) {
        Text("Address details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (state.detailLoading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading ${state.selected}…") }
        state.detailError?.let {
            MessageCard(it, error = true)
            OutlinedButton(onClick = { select(state.selected) }, enabled = !state.busy) { Text("Retry address") }
        }
        state.detail?.let { detail ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${detail.name}.omg.lol", style = MaterialTheme.typography.titleMedium)
                    DetailRow("Registered", detail.registered)
                    DetailRow("Expiration", detail.expiration)
                    DetailRow("Verified", when (detail.verified) { true -> "Yes"; false -> "No"; null -> "Unknown" })
                }
            }
        }
    }
}
@Composable private fun DetailRow(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value) }
}
@Composable private fun MessageCard(message: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(16.dp), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
