/*
 * Round Keyboard for Wear OS
 * Copyright (C) 2026 [stanissay]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package stanissay.wear.board

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
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
            MainCard(
                transformationSpec = transformationSpec,
                contentPadding = MainConstants.NULL_PADDING
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MainText(text = stringResource(R.string.use_t9))
                    Switch(
                        checked = viewModel.useT9,
                        onCheckedChange = viewModel::updateUseT9
                    )
                }
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
    val dictionaryStatus by viewModel.dictionaryStatus.collectAsState()

    BackHandler { viewModel.showDictionaries = false }

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
                contentPadding = MainConstants.NULL_PADDING,
                borderColor = MaterialTheme.colors.surface,
                containerColor = MainColors.TRANSPARENT
            ) {
                ClickableBox(
                    modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE),
                    onClick = { viewModel.downloadDictionary(KeyboardLayout.ENGLISH) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MainText(text = stringResource(R.string.keyboard_english))
                        CaptureText(
                            text = when (dictionaryStatus[KeyboardLayout.ENGLISH]) {
                                DictionaryStatus.NOT_LOADED -> stringResource(R.string.dictionary_download)
                                DictionaryStatus.DOWNLOADING -> stringResource(R.string.dictionary_downloading)
                                DictionaryStatus.IMPORTING -> stringResource(R.string.dictionary_importing)
                                DictionaryStatus.LOADED -> stringResource(R.string.dictionary_loaded)
                                DictionaryStatus.ERROR -> stringResource(R.string.dictionary_error)
                                null -> ""
                            }
                        )
                    }
                }
            }
        }
        item {
            MainCard(
                transformationSpec = transformationSpec,
                contentPadding = MainConstants.NULL_PADDING,
                borderColor = MaterialTheme.colors.surface,
                containerColor = MainColors.TRANSPARENT
            ) {
                ClickableBox(
                    modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE),
                    onClick = { viewModel.downloadDictionary(KeyboardLayout.UKRAINIAN) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MainText(text = stringResource(R.string.keyboard_ukrainian))
                        CaptureText(
                            text = when (dictionaryStatus[KeyboardLayout.UKRAINIAN]) {
                                DictionaryStatus.NOT_LOADED -> stringResource(R.string.dictionary_download)
                                DictionaryStatus.DOWNLOADING -> stringResource(R.string.dictionary_downloading)
                                DictionaryStatus.IMPORTING -> stringResource(R.string.dictionary_importing)
                                DictionaryStatus.LOADED -> stringResource(R.string.dictionary_loaded)
                                DictionaryStatus.ERROR -> stringResource(R.string.dictionary_error)
                                null -> ""
                            }
                        )
                    }
                }
            }
        }
        item { WearSpacer(transformationSpec) }
    }
}