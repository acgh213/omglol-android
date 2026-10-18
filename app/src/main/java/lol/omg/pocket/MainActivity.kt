package lol.omg.pocket

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import lol.omg.pocket.data.LiveAccountRepository
import lol.omg.pocket.security.KeystoreCredentialStore
import lol.omg.pocket.ui.AccountViewModel
import lol.omg.pocket.ui.PocketApp
import lol.omg.pocket.ui.StatusViewModel

class MainActivity : ComponentActivity() {
    private val model: AccountViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AccountViewModel(LiveAccountRepository(), KeystoreCredentialStore(applicationContext)) as T
        }
    }
    private val statuses: StatusViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { PocketApp(model, statuses, ::protectScreenshots) }
    }
    private fun protectScreenshots(protect: Boolean) {
        if (protect) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
