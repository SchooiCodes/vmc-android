// Top-level build file. Plugins are declared here (not applied) so each
// module opts in via the `plugins {}` block with `apply false`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
