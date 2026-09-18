package stanissay.wear.board

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material3.lazy.rememberTransformationSpec

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAllSubtypes()
        setContent {
            MainTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val state = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    TransformingLazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colors.background),
        state = state,
        contentPadding = PaddingValues(Constants.MAIN_PADDING)
    ) {
        item { WearSpacer(transformationSpec) }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(Constants.BASE_SIZE)
                    .then(transformedItem(transformationSpec)),
                contentAlignment = Alignment.Center
            ) { TitleText(text = stringResource(R.string.app_name)) }
        }
        item {
            MainCard(
                transformationSpec = transformationSpec,
                contentPadding = Constants.NULL_PADDING
            ) {
                ClickableBox(
                    modifier = Modifier.fillMaxWidth().height(Constants.BASE_SIZE),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        )
                    }
                ) {
                    MainText(
                        text = stringResource(R.string.enable_in_settings),
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
        }
        item { WearSpacer(transformationSpec) }
    }
}