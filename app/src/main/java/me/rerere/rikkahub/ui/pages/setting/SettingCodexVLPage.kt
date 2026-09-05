package me.rerere.rikkahub.ui.pages.setting

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.codexvl.CodexVLConfigStore
import me.rerere.rikkahub.data.codexvl.CodexVLConnectionTester
import me.rerere.rikkahub.data.codexvl.CodexVLProviderConfig
import me.rerere.rikkahub.data.codexvl.CodexVLRuntimeManager
import me.rerere.rikkahub.data.codexvl.CodexVLRuntimeMode
import me.rerere.rikkahub.data.codexvl.CodexVLSetupCommandParser
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun SettingCodexVLPage() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val store: CodexVLConfigStore = koinInject()
    val tester: CodexVLConnectionTester = koinInject()
    val runtime: CodexVLRuntimeManager = koinInject()
    val initial = remember { store.read() }
    var config by remember { mutableStateOf(initial.provider) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var command by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var showRootWarning by remember { mutableStateOf(false) }
    val runtimeStatus by runtime.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showRootWarning) {
        AlertDialog(
            onDismissRequest = { showRootWarning = false },
            title = { Text(stringResource(R.string.codex_vl_root_warning_title)) },
            text = { Text(stringResource(R.string.codex_vl_root_warning_text)) },
            confirmButton = {
                Button(onClick = {
                    config = config.copy(rootAccessEnabled = true)
                    showRootWarning = false
                }) { Text(stringResource(R.string.codex_vl_enable_root)) }
            },
            dismissButton = {
                TextButton(onClick = { showRootWarning = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.codex_vl_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scroll,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardGroup(title = { Text(stringResource(R.string.codex_vl_runtime)) }) {
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_enable)) },
                    supportingContent = { Text(stringResource(R.string.codex_vl_enable_desc)) },
                    trailingContent = {
                        Switch(checked = config.enabled, onCheckedChange = { config = config.copy(enabled = it) })
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_runtime_status)) },
                    supportingContent = { Text(runtimeStatus.label()) },
                )
            }

            CardGroup(title = { Text(stringResource(R.string.codex_vl_runtime_mode)) }) {
                item(
                    onClick = { config = config.copy(runtimeMode = CodexVLRuntimeMode.BUNDLED) },
                    headlineContent = { Text(stringResource(R.string.codex_vl_bundled_runtime)) },
                    leadingContent = {
                        RadioButton(
                            selected = config.runtimeMode == CodexVLRuntimeMode.BUNDLED,
                            onClick = { config = config.copy(runtimeMode = CodexVLRuntimeMode.BUNDLED) },
                        )
                    },
                )
                item(
                    onClick = { config = config.copy(runtimeMode = CodexVLRuntimeMode.EXTERNAL) },
                    headlineContent = { Text(stringResource(R.string.codex_vl_external_runtime)) },
                    leadingContent = {
                        RadioButton(
                            selected = config.runtimeMode == CodexVLRuntimeMode.EXTERNAL,
                            onClick = { config = config.copy(runtimeMode = CodexVLRuntimeMode.EXTERNAL) },
                        )
                    },
                )
            }
            if (config.runtimeMode == CodexVLRuntimeMode.EXTERNAL) {
                OutlinedTextField(
                    value = config.externalRuntimePath,
                    onValueChange = { config = config.copy(externalRuntimePath = it) },
                    label = { Text(stringResource(R.string.codex_vl_external_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            CardGroup(title = { Text(stringResource(R.string.codex_vl_import_command)) }) {
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_import_command)) },
                    supportingContent = { Text(stringResource(R.string.codex_vl_import_command_desc)) },
                )
            }
            OutlinedTextField(
                value = command,
                onValueChange = { if (it.length <= CodexVLSetupCommandParser.MAX_INPUT_LENGTH) command = it },
                label = { Text(stringResource(R.string.codex_vl_paste_command)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = {
                    when (val parsed = CodexVLSetupCommandParser.parse(command)) {
                        is CodexVLSetupCommandParser.Result.Success -> {
                            config = config.copy(
                                baseUrl = parsed.value.baseUrl,
                                model = parsed.value.model ?: config.model,
                            )
                            apiKey = parsed.value.apiKey
                            command = ""
                            resultText = if (parsed.value.unsupportedOptions.isEmpty()) {
                                "Responses API · ${parsed.value.baseUrl}"
                            } else {
                                "${parsed.value.baseUrl} · ${parsed.value.unsupportedOptions.joinToString()}"
                            }
                        }
                        is CodexVLSetupCommandParser.Result.Failure -> {
                            resultText = "${parsed.reason}"
                        }
                    }
                },
                enabled = command.isNotBlank(),
            ) { Text(stringResource(R.string.codex_vl_parse)) }

            CardGroup(title = { Text(stringResource(R.string.codex_vl_provider)) }) {
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_protocol)) },
                    supportingContent = { Text("Responses API") },
                )
            }
            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = { config = config.copy(baseUrl = it) },
                label = { Text(stringResource(R.string.codex_vl_base_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.codex_vl_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (apiKey.isNotEmpty()) {
                TextButton(onClick = { apiKey = "" }) { Text(stringResource(R.string.codex_vl_clear_key)) }
            }
            OutlinedTextField(
                value = config.model,
                onValueChange = { config = config.copy(model = it) },
                label = { Text(stringResource(R.string.codex_vl_model)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            CardGroup(title = { Text(stringResource(R.string.codex_vl_tools)) }) {
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_android_tools)) },
                    trailingContent = {
                        Switch(config.androidToolsEnabled, { config = config.copy(androidToolsEnabled = it) })
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_root_access)) },
                    supportingContent = { Text(stringResource(R.string.codex_vl_root_access_desc)) },
                    trailingContent = {
                        Switch(config.rootAccessEnabled, {
                            if (it) showRootWarning = true else config = config.copy(rootAccessEnabled = false)
                        })
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_debug_logs)) },
                    trailingContent = {
                        Switch(config.debugLogsEnabled, { config = config.copy(debugLogsEnabled = it) })
                    },
                )
            }

            if (resultText.isNotBlank()) Text(resultText)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        scope.launch {
                            resultText = when (val result = tester.test(config, apiKey)) {
                                is CodexVLConnectionTester.Result.Success -> "Connected (${result.httpStatus})"
                                is CodexVLConnectionTester.Result.Failure -> result.error.name.replace('_', ' ')
                            }
                            testing = false
                        }
                    },
                ) { Text(stringResource(R.string.codex_vl_test_connection)) }
                Button(onClick = {
                    // A runtime turn may persist a new conversation -> Codex thread
                    // mapping while this page is open. Re-read it so saving provider
                    // settings cannot roll durable session state back.
                    val latestThreads = store.read().conversationThreads
                    store.write(CodexVLConfigStore.State(config, apiKey, latestThreads))
                    resultText = "Saved"
                    if (config.enabled) scope.launch { runtime.restart() }
                    else scope.launch { runtime.stop() }
                }) { Text(stringResource(R.string.codex_vl_save_enable)) }
            }
        }
    }
}

private fun CodexVLRuntimeManager.Status.label(): String = when (this) {
    CodexVLRuntimeManager.Status.Stopped -> "Stopped"
    CodexVLRuntimeManager.Status.Starting -> "Starting"
    is CodexVLRuntimeManager.Status.Running -> "Running · $version"
    is CodexVLRuntimeManager.Status.Unavailable -> "Unavailable · $reason"
    is CodexVLRuntimeManager.Status.Crashed -> "Crashed · exit $exitCode"
    is CodexVLRuntimeManager.Status.Failed -> message
}
