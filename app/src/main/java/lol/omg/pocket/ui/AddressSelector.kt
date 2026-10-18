package lol.omg.pocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import lol.omg.pocket.data.Address

@Composable fun AddressSelector(addresses: List<Address>, selected: String?, enabled: Boolean, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(selected, enabled, addresses) { expanded = false }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && addresses.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("address-selector")) {
            Column(Modifier.weight(1f)) {
                Text("Selected address", style = MaterialTheme.typography.labelSmall)
                Text(selected?.let { "$it.omg.lol" } ?: "No addresses")
            }
            Text("⌄")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            addresses.forEach { address ->
                DropdownMenuItem(text = {
                    Column { Text("${address.name}.omg.lol"); Text(address.expiration, style = MaterialTheme.typography.bodySmall) }
                }, onClick = { expanded = false; select(address.name) }, modifier = Modifier.testTag("select-${address.name}"))
            }
        }
    }
}
