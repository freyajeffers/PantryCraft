package com.jeffers.pantrycraft.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import com.jeffers.pantrycraft.feature.camera.vision.LiveVisionPreferences

@Composable
fun AiSettingsCard(context: Context) {
    var selectedAiProvider by remember { mutableStateOf(ByokPreferences.selectedProvider(context)) }
    var aiModelName by remember { mutableStateOf(ByokPreferences.selectedModel(context)) }
    var aiEndpointUrl by remember { mutableStateOf(ByokPreferences.selectedEndpoint(context)) }
    var aiApiKeyInput by remember { mutableStateOf("") }
    var hasStoredApiKey by remember { mutableStateOf(ByokPreferences.hasApiKey(context)) }
    var isGeminiLive by remember { mutableStateOf(LiveVisionPreferences.isGeminiLiveEnabled(context)) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiSettingsHeader()
            AiProviderSelector(selectedAiProvider) { provider ->
                selectedAiProvider = provider
                aiModelName = provider.defaultModel
                aiEndpointUrl = provider.defaultEndpoint
            }
            if (selectedAiProvider != AiProvider.OFFLINE_MLKIT) {
                AiCustomFields(
                    props =
                        AiCustomFieldsProps(
                            provider = selectedAiProvider,
                            modelName = aiModelName,
                            onModelNameChange = { aiModelName = it },
                            endpointUrl = aiEndpointUrl,
                            onEndpointUrlChange = { aiEndpointUrl = it },
                            apiKey = aiApiKeyInput,
                            onApiKeyChange = { aiApiKeyInput = it },
                            hasStoredApiKey = hasStoredApiKey,
                        ),
                )
            }
            if (selectedAiProvider == AiProvider.GEMINI) {
                GeminiLiveToggleRow(isGeminiLive) { isGeminiLive = it }
            }
            AiSettingsActions(hasStoredApiKey, onSave = {
                ByokPreferences.saveConfiguration(context, selectedAiProvider, aiModelName, aiEndpointUrl)
                LiveVisionPreferences.setGeminiLiveEnabled(context, isGeminiLive)
                if (aiApiKeyInput.isNotBlank()) {
                    ByokPreferences.saveApiKey(context, aiApiKeyInput.toCharArray())
                    aiApiKeyInput = ""
                    hasStoredApiKey = ByokPreferences.hasApiKey(context)
                }
                Toast.makeText(context, "AI configuration saved", Toast.LENGTH_SHORT).show()
            }, onClearKey = {
                ByokPreferences.clearApiKey(context)
                hasStoredApiKey = false
                aiApiKeyInput = ""
                Toast.makeText(context, "Stored API key cleared", Toast.LENGTH_SHORT).show()
            })
        }
    }
}

@Composable
private fun AiSettingsHeader() {
    Text("AI Vision & Meal Engine (BYOKey / Local)", style = MaterialTheme.typography.titleLarge)
    Text(
        "Choose between 100% offline on-device ML Kit inference or your own personal API key (BYOKey) " +
            "for shelf snapshot decomposition and Phase 3 meal synthesis. API keys are encrypted with " +
            "Android KeyStore StrongBox/TEE and never routed through proxy servers.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AiProviderSelector(
    selectedProvider: AiProvider,
    onSelect: (AiProvider) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiProvider.entries.forEach { provider ->
            if (selectedProvider == provider) {
                Button(onClick = { onSelect(provider) }) {
                    Text("✓ ${provider.label}")
                }
            } else {
                OutlinedButton(onClick = { onSelect(provider) }) {
                    Text(provider.label)
                }
            }
        }
    }
}

internal data class AiCustomFieldsProps(
    val provider: AiProvider,
    val modelName: String,
    val onModelNameChange: (String) -> Unit,
    val endpointUrl: String,
    val onEndpointUrlChange: (String) -> Unit,
    val apiKey: String,
    val onApiKeyChange: (String) -> Unit,
    val hasStoredApiKey: Boolean,
)

@Composable
private fun AiCustomFields(props: AiCustomFieldsProps) {
    OutlinedTextField(
        value = props.modelName,
        onValueChange = props.onModelNameChange,
        label = { Text("Model Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = props.endpointUrl,
        onValueChange = props.onEndpointUrlChange,
        label = { Text("Endpoint URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (props.provider.requiresApiKey) {
        OutlinedTextField(
            value = props.apiKey,
            onValueChange = props.onApiKeyChange,
            label = {
                Text(
                    if (props.hasStoredApiKey) {
                        "API Key (Encrypted key saved · enter new key to replace)"
                    } else {
                        "API Key"
                    },
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AiSettingsActions(
    hasStoredApiKey: Boolean,
    onSave: () -> Unit,
    onClearKey: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
            Text("Save AI Settings")
        }
        if (hasStoredApiKey) {
            OutlinedButton(onClick = onClearKey) {
                Text("Clear Key")
            }
        }
    }
}

@Composable
private fun GeminiLiveToggleRow(
    isGeminiLiveEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Checkbox(
            checked = isGeminiLiveEnabled,
            onCheckedChange = onToggle,
        )
        Text(
            text = "Enable Gemini Live Vision (WebSocket streaming)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
