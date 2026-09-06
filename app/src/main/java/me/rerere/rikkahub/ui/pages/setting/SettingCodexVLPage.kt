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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
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
                    supportingContent = { Text(runtimeStatus.localizedLabel()) },
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
                                context.getString(R.string.codex_vl_parse_success, parsed.value.baseUrl)
                            } else {
                                context.getString(
                                    R.string.codex_vl_parse_success_unsupported,
                                    parsed.value.baseUrl,
                                    parsed.value.unsupportedOptions.joinToString(),
                                )
                            }
                        }
                        is CodexVLSetupCommandParser.Result.Failure -> {
                            resultText = context.getString(parsed.reason.messageResource())
                        }
                    }
                },
                enabled = command.isNotBlank(),
            ) { Text(stringResource(R.string.codex_vl_parse)) }

            CardGroup(title = { Text(stringResource(R.string.codex_vl_provider)) }) {
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_protocol)) },
                    supportingContent = { Text(stringResource(R.string.codex_vl_protocol_responses)) },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.codex_vl_preset_provider)) },
                    supportingContent = { Text(stringResource(R.string.codex_vl_preset_provider_desc)) },
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
                                is CodexVLConnectionTester.Result.Success -> context.getString(
                                    R.string.codex_vl_connection_success,
                                    result.httpStatus,
                                )
                                is CodexVLConnectionTester.Result.Failure -> {
                                    val message = context.getString(result.error.messageResource())
                                    result.httpStatus?.let {
                                        context.getString(R.string.codex_vl_error_with_status, message, it)
                                    } ?: message
                                }
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
                    resultText = context.getString(R.string.codex_vl_saved)
                    if (config.enabled) scope.launch { runtime.restart() }
                    else scope.launch { runtime.stop() }
                }) { Text(stringResource(R.string.codex_vl_save_enable)) }
            }
        }
    }
}

@Composable
private fun CodexVLRuntimeManager.Status.localizedLabel(): String = when (this) {
    CodexVLRuntimeManager.Status.Stopped -> stringResource(R.string.codex_vl_status_stopped)
    CodexVLRuntimeManager.Status.Starting -> stringResource(R.string.codex_vl_status_starting)
    is CodexVLRuntimeManager.Status.Running -> stringResource(
        R.string.codex_vl_status_running,
        version,
    )
    is CodexVLRuntimeManager.Status.Unavailable -> stringResource(
        R.string.codex_vl_status_unavailable,
        localizedRuntimeReason(reason),
    )
    is CodexVLRuntimeManager.Status.Crashed -> stringResource(
        R.string.codex_vl_status_crashed,
        exitCode,
    )
    is CodexVLRuntimeManager.Status.Failed -> stringResource(
        R.string.codex_vl_status_failed,
        localizedRuntimeReason(message),
    )
}

@Composable
private fun localizedRuntimeReason(reason: String): String = stringResource(
    when (reason) {
        "Runtime path is not configured" -> R.string.codex_vl_runtime_reason_path_not_configured
        "Runtime executable is missing" -> R.string.codex_vl_runtime_reason_missing
        "Runtime is not executable" -> R.string.codex_vl_runtime_reason_not_executable
        "Codex-VL is disabled" -> R.string.codex_vl_runtime_reason_disabled
        "Provider configuration is incomplete" -> R.string.codex_vl_runtime_reason_provider_incomplete
        "Codex runtime unavailable" -> R.string.codex_vl_runtime_reason_unavailable
        else -> R.string.codex_vl_runtime_reason_failed
    },
)

private fun CodexVLSetupCommandParser.FailureReason.messageResource(): Int = when (this) {
    CodexVLSetupCommandParser.FailureReason.EMPTY -> R.string.codex_vl_parse_error_empty
    CodexVLSetupCommandParser.FailureReason.TOO_LONG -> R.string.codex_vl_parse_error_too_long
    CodexVLSetupCommandParser.FailureReason.SHELL_SYNTAX -> R.string.codex_vl_parse_error_shell_syntax
    CodexVLSetupCommandParser.FailureReason.INVALID_COMMAND -> R.string.codex_vl_parse_error_invalid_command
    CodexVLSetupCommandParser.FailureReason.MISSING_URL -> R.string.codex_vl_parse_error_missing_url
    CodexVLSetupCommandParser.FailureReason.MISSING_KEY -> R.string.codex_vl_parse_error_missing_key
    CodexVLSetupCommandParser.FailureReason.MISSING_VALUE -> R.string.codex_vl_parse_error_missing_value
    CodexVLSetupCommandParser.FailureReason.DUPLICATE_OPTION -> R.string.codex_vl_parse_error_duplicate
    CodexVLSetupCommandParser.FailureReason.INVALID_URL -> R.string.codex_vl_parse_error_invalid_url
    CodexVLSetupCommandParser.FailureReason.INVALID_KEY -> R.string.codex_vl_parse_error_invalid_key
    CodexVLSetupCommandParser.FailureReason.INVALID_MODEL -> R.string.codex_vl_parse_error_invalid_model
}

private fun CodexVLConnectionTester.Error.messageResource(): Int = when (this) {
    CodexVLConnectionTester.Error.INVALID_URL -> R.string.codex_vl_error_invalid_url
    CodexVLConnectionTester.Error.MISSING_API_KEY -> R.string.codex_vl_error_missing_api_key
    CodexVLConnectionTester.Error.AUTHENTICATION_FAILED -> R.string.codex_vl_error_authentication_failed
    CodexVLConnectionTester.Error.ACCESS_DENIED -> R.string.codex_vl_error_access_denied
    CodexVLConnectionTester.Error.ENDPOINT_OR_MODEL_NOT_FOUND -> R.string.codex_vl_error_endpoint_or_model_not_found
    CodexVLConnectionTester.Error.MODEL_UNAVAILABLE -> R.string.codex_vl_error_model_unavailable
    CodexVLConnectionTester.Error.RATE_LIMITED -> R.string.codex_vl_error_rate_limited
    CodexVLConnectionTester.Error.PROVIDER_ERROR -> R.string.codex_vl_error_provider
    CodexVLConnectionTester.Error.TIMEOUT -> R.string.codex_vl_error_timeout
    CodexVLConnectionTester.Error.DNS_FAILURE -> R.string.codex_vl_error_dns
    CodexVLConnectionTester.Error.TLS_FAILURE -> R.string.codex_vl_error_tls
    CodexVLConnectionTester.Error.INVALID_JSON -> R.string.codex_vl_error_invalid_json
    CodexVLConnectionTester.Error.MALFORMED_STREAM -> R.string.codex_vl_error_malformed_stream
    CodexVLConnectionTester.Error.BAD_REQUEST -> R.string.codex_vl_error_bad_request
    CodexVLConnectionTester.Error.HTTP_ERROR -> R.string.codex_vl_error_http
    CodexVLConnectionTester.Error.NETWORK_FAILURE -> R.string.codex_vl_error_network
}
