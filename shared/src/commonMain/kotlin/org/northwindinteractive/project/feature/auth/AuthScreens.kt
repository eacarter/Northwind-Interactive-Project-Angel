package org.northwindinteractive.project.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.northwindinteractive.project.core.designsystem.AngelTheme
import org.northwindinteractive.project.core.designsystem.AngelTopBar
import org.northwindinteractive.project.core.designsystem.IconBadge
import org.northwindinteractive.project.core.designsystem.InfoNote
import org.northwindinteractive.project.core.designsystem.PrimaryButton
import org.northwindinteractive.project.core.designsystem.SecondaryButton
import org.northwindinteractive.project.core.designsystem.Spacing
import org.northwindinteractive.project.core.designsystem.Tone
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.angel_wings
import org.northwindinteractive.project.resources.ic_arrow_right
import org.northwindinteractive.project.resources.ic_lock
import org.northwindinteractive.project.resources.ic_map_pin
import org.northwindinteractive.project.resources.ic_users
import org.northwindinteractive.project.resources.ill_nightlife_hero

/** Welcome / sign in (spec: wing logo, brand statement, background scene, sign-in and account creation). */
@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    isDemo: Boolean,
) {
    val colors = AngelTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
            Image(
                painter = painterResource(Res.drawable.ill_nightlife_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to colors.canvas.copy(alpha = 0f), 1f to colors.canvas),
                ),
            )
            Column(
                modifier = Modifier.align(Alignment.Center).statusBarsPadding().padding(top = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(Res.drawable.angel_wings),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
                Text(
                    "Project Angel",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.content,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    "Discreet safety alerts at participating venues.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.contentMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FeatureLine(Res.drawable.ic_lock, "Send discreet alerts to venue security")
            FeatureLine(Res.drawable.ic_map_pin, "Only works while you're inside the venue")
            FeatureLine(Res.drawable.ic_users, "Designed for safer nights out")
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton("Sign In", onClick = onSignIn, trailingIcon = Res.drawable.ic_arrow_right)
            SecondaryButton("Create an Account", onClick = onCreateAccount)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                buildAnnotatedString {
                    append("By continuing, you agree to our ")
                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) { append("Terms") }
                    append(" and ")
                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") }
                    append(". Project Angel is not a replacement for emergency services.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isDemo) {
                InfoNote("Demo mode: Firebase isn't connected yet. Accounts and venues are simulated on this device.")
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun FeatureLine(icon: DrawableResource, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        IconBadge(icon, Tone.PRIMARY, size = 36)
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AngelTheme.colors.content)
    }
}

@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearMessages() }
    AuthScaffold(title = "Sign In", onBack = onBack) {
        Header("Welcome back", "Sign in to use Project Angel at participating venues.")
        EmailField(form.email, viewModel::onEmailChange)
        PasswordField(form.password, viewModel::onPasswordChange, ImeAction.Done, onDone = viewModel::signIn)
        Messages(form)
        PrimaryButton(
            "Sign In",
            onClick = viewModel::signIn,
            enabled = form.canSignIn,
            loading = form.isSubmitting,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = viewModel::sendPasswordReset) {
                Text("Forgot password?", color = AngelTheme.colors.primary)
            }
            TextButton(onClick = onCreateAccount) {
                Text("Create an account", color = AngelTheme.colors.primary)
            }
        }
    }
}

@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearMessages() }
    AuthScaffold(title = "Create Account", onBack = onBack) {
        Header("Create your account", "We keep personal details to a minimum. An alias is optional.")
        OutlinedTextField(
            value = form.displayAlias,
            onValueChange = viewModel::onAliasChange,
            label = { Text("Alias (optional)") },
            singleLine = true,
            colors = fieldColors(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(form.email, viewModel::onEmailChange)
        PasswordField(form.password, viewModel::onPasswordChange, ImeAction.Done, onDone = viewModel::createAccount)
        Text(
            "At least ${AuthFormState.MIN_PASSWORD} characters.",
            style = MaterialTheme.typography.bodySmall,
            color = AngelTheme.colors.contentMuted,
        )
        Messages(form)
        PrimaryButton(
            "Create Account",
            onClick = viewModel::createAccount,
            enabled = form.canCreate,
            loading = form.isSubmitting,
        )
        TextButton(onClick = onSignIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("I already have an account", color = AngelTheme.colors.primary)
        }
        Text(
            "By creating an account, you agree to our Terms and Privacy Policy.",
            style = MaterialTheme.typography.bodySmall,
            color = AngelTheme.colors.contentMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AngelTheme.colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AngelTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(Res.drawable.angel_wings), contentDescription = null, modifier = Modifier.size(64.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = AngelTheme.colors.content,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = AngelTheme.colors.contentMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Messages(form: AuthFormState) {
    form.error?.let {
        Text(
            it,
            color = AngelTheme.colors.danger,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    form.info?.let {
        Text(
            it,
            color = AngelTheme.colors.success,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AngelTheme.colors.card,
    unfocusedContainerColor = AngelTheme.colors.card,
    focusedBorderColor = AngelTheme.colors.primary,
    unfocusedBorderColor = AngelTheme.colors.cardBorder,
)

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Email") },
        singleLine = true,
        colors = fieldColors(),
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, imeAction: ImeAction, onDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Password") },
        singleLine = true,
        colors = fieldColors(),
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Hide" else "Show", color = AngelTheme.colors.primary)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
