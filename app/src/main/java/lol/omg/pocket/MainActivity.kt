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
    private val session = lol.omg.pocket.security.AccountSession()
    private val demo = lol.omg.pocket.data.DemoStatusRepository()
    private val features by lazy { lol.omg.pocket.data.LiveFeatureRepository(session, demo = demo) }
    private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
    private val model: AccountViewModel by viewModels {
        factory { AccountViewModel(LiveAccountRepository(), KeystoreCredentialStore(applicationContext), session = session) }
    }
    private val statuses: StatusViewModel by viewModels { factory { StatusViewModel(demo = demo) } }
    private val composer: lol.omg.pocket.ui.ComposerViewModel by viewModels { factory { lol.omg.pocket.ui.ComposerViewModel(features) } }
    private val profile: lol.omg.pocket.ui.ProfileViewModel by viewModels { factory { lol.omg.pocket.ui.ProfileViewModel(features) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { PocketApp(model, statuses, composer, profile, ::protectScreenshots) }
    }
    private fun protectScreenshots(protect: Boolean) {
        if (protect) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
