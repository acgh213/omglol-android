package lol.omg.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Lilac = Color(0xFF6853A0)
@Composable fun PocketApp(
    model: AccountViewModel,
    statusModel: StatusViewModel,
    composerModel: ComposerViewModel,
    profileModel: ProfileViewModel,
    onScreenshotProtection: (Boolean) -> Unit = {}
) {
    val state by model.state.collectAsStateWithLifecycle()
    val status by statusModel.state.collectAsStateWithLifecycle()
    val composer by composerModel.state.collectAsStateWithLifecycle()
    val profile by profileModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf("Account") }
    val protectCredentials = state.account == null
    DisposableEffect(protectCredentials, onScreenshotProtection) {
        onScreenshotProtection(protectCredentials)
        onDispose { onScreenshotProtection(true) }
    }
    LaunchedEffect(state.scope) {
        statusModel.setScope(state.scope)
        composerModel.setScope(state.scope)
    }
    LaunchedEffect(state.scope, destination) { profileModel.setScope(state.scope, destination == "Profile") }
    LaunchedEffect(composer.receipt) {
        if (composer.receipt != null && composer.scope == state.scope) statusModel.refresh()
    }
    LaunchedEffect(state.account == null) { if (state.account == null) destination = "Account" }
    MaterialTheme(colorScheme = lightColorScheme(primary = Lilac, secondary = Color(0xFF4E7068),
        background = Color(0xFFF9F7FC), surface = Color(0xFFF9F7FC))) {
        Scaffold(bottomBar = {
            if (state.account != null) NavigationBar {
                NavigationBarItem(selected = destination == "Account", onClick = { destination = "Account" },
                    icon = { Text("⌂") }, label = { Text("Account") })
                NavigationBarItem(selected = destination == "Statuslog", onClick = { destination = "Statuslog" },
                    icon = { Text("☕") }, label = { Text("Statuslog") })
                NavigationBarItem(selected = destination == "Profile", onClick = { destination = "Profile" },
                    icon = { Text("✎") }, label = { Text("Profile") })
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, color = Lilac) {
                        Text(":)", Modifier.padding(12.dp), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("omg.lol pocket", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                if (state.account != null) {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.demo) Text("Demo mode · Sample data", style = MaterialTheme.typography.labelMedium)
                        AddressSelector(state.addresses, state.selected, !state.busy, model::selectAddress)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.restoring -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking saved sign-in…")
                        }
                        state.account == null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            SignIn(state, model::signIn, model::demo, model::refresh)
                            if (state.busy) TextButton(onClick = model::signOut) { Text("Cancel") }
                            Text("Independent omg.lol client", style = MaterialTheme.typography.labelSmall)
                        }
                        destination == "Statuslog" -> StatusScreen(state.scope, status, statusModel::refresh, statusModel::retry, composerModel::open)
                        destination == "Profile" -> ProfileScreen(state.scope, profile, profileModel::refresh, profileModel::openEditor)
                        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            AccountScreen(state, model::refresh, model::selectAddress, model::signOut)
                            Text("Independent omg.lol client", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (profile.editorOpen && profile.scope == state.scope) ProfileEditorDialog(profile, profileModel)
        if (composer.open && state.scope != null && composer.scope == state.scope) ComposerDialog(composer, composerModel)
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
    Text("Use the API key from your omg.lol account settings. Remembering sign-in stores it encrypted on this device. Your API key is sent only to omg.lol.",
        style = MaterialTheme.typography.bodySmall)
    Button(onClick = { signIn(email, key, rememberSignIn); key = "" }, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if (state.busy) "Connecting…" else "Connect account") }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    OutlinedButton(onClick = { email = ""; key = ""; demo() }, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Try demo") }
}

@Composable private fun AccountScreen(state: AccountUiState, refresh: () -> Unit, select: (String) -> Unit, signOut: () -> Unit) {
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
    if (state.addresses.isEmpty()) MessageCard("No addresses belong to this account yet.")
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
@Composable internal fun MessageCard(message: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(16.dp), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
