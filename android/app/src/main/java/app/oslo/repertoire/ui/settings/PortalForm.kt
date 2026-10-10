package app.oslo.repertoire.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.ui.components.MessageCard

/**
 * Xtream portal login. The login is checked against the server before it is saved; failures
 * (HTTP 403 and friends) explain what to check. A blank password keeps the saved one.
 */
@Composable
fun PortalForm(model: GuideViewModel, submitLabel: String, onSuccess: () -> Unit, modifier: Modifier = Modifier) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        model.signIn(onSuccess)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = model.portalUrl,
            onValueChange = { model.portalUrl = it; model.portalError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server URL") },
            placeholder = { Text("http://provider.example:8080") },
            leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
            singleLine = true,
            isError = model.portalError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            value = model.username,
            onValueChange = { model.username = it; model.portalError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            value = model.password,
            onValueChange = { model.password = it; model.portalError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            placeholder = if (model.hasPortal) ({ Text("Saved — leave blank to keep it") }) else null,
            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        AnimatedVisibility(model.portalError != null) {
            MessageCard(text = model.portalError.orEmpty(), title = "Couldn't sign in")
        }
        Button(onClick = submit, enabled = !model.signingIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (model.signingIn) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text("Checking login…")
            } else {
                Text(submitLabel)
            }
        }
        Text(
            "Use the Xtream Codes login from your provider, the same one TiviMate uses. " +
                "It's stored encrypted on this phone and only sent to your server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(model: GuideViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("IPTV portal") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        PortalForm(
            model = model,
            submitLabel = if (model.hasPortal) "Save and refresh" else "Sign in",
            onSuccess = onBack,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
