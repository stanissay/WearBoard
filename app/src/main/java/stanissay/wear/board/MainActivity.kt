package stanissay.wear.board

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAllSubtypes()
        setContent {
            MainTheme {
                if(!viewModel.showDictionaries) {
                    MainScreen(viewModel)
                } else {
                    DictionariesScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val versionName = context.packageManager
        .getPackageInfo(context.packageName, 0).versionName ?: "0.0"

    TransformingLazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colors.background),
        state = state,
        contentPadding = PaddingValues(MainConstants.MAIN_PADDING)
    ) {
        item { WearSpacer(transformationSpec) }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE)
                    .then(transformedItem(transformationSpec)),
                contentAlignment = Alignment.Center
            ) { TitleText(text = stringResource(R.string.app_name)) }
        }
        item {
            MainCard(
                transformationSpec = transformationSpec,
                contentPadding = MainConstants.NULL_PADDING
            ) {
                ClickableBox(
                    modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE),
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
        item {
            MainCard(
                transformationSpec = transformationSpec,
                contentPadding = MainConstants.NULL_PADDING
            ) {
                ClickableBox(
                    modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE),
                    onClick = { viewModel.showDictionaries = true }
                ) { MainText(text = stringResource(R.string.dictionaries)) }
            }
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE)
                    .then(transformedItem(transformationSpec)),
                contentAlignment = Alignment.Center
            ) {
                MainText(
                    text = stringResource(R.string.version) + versionName,
                    color = MaterialTheme.colors.surface
                )
            }
        }
        item { WearSpacer(transformationSpec) }
    }
}

@Composable
fun DictionariesScreen(viewModel: MainViewModel) {
    val state = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    TransformingLazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colors.background),
        state = state,
        contentPadding = PaddingValues(MainConstants.MAIN_PADDING)
    ) {
        item { WearSpacer(transformationSpec) }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE)
                    .then(transformedItem(transformationSpec)),
                contentAlignment = Alignment.Center
            ) { TitleText(text = stringResource(R.string.dictionaries)) }
        }
        item {
            MainCard(
                transformationSpec = transformationSpec,
                contentPadding = MainConstants.NULL_PADDING
            ) {
                ClickableBox(
                    modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE),
                    onClick = {
                        if(!viewModel.isDictionaryLoaded(KeyboardLayout.ENGLISH)) {
                            viewModel.downloadDictionary(KeyboardLayout.ENGLISH)
                        }
                    }
                ) {
                    MainText(
                        text = stringResource(R.string.keyboard_english)
                    )
                }
                ClickableBox(
                    modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE),
                    onClick = {
                        if(!viewModel.isDictionaryLoaded(KeyboardLayout.UKRAINIAN)) {
                            viewModel.downloadDictionary(KeyboardLayout.UKRAINIAN)
                        }
                    }
                ) {
                    MainText(
                        text = stringResource(R.string.keyboard_ukrainian)
                    )
                }
            }
        }
        item { WearSpacer(transformationSpec) }
    }
}