package fr.istic.mob.networkBN

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.istic.mob.networkBN.ui.theme.NetworkBNTheme
import fr.istic.mob.networkBN.viewModel.GraphViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkBNTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EcranReseau(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** Écran principal. Le ViewModel survit aux rotations d'écran. */
@Composable
fun EcranReseau(
    modifier: Modifier = Modifier,
    viewModel: GraphViewModel = viewModel()
) {
    Text(text = "NetworkBN", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun EcranReseauPreview() {
    NetworkBNTheme {
        EcranReseau()
    }
}
