plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// ---------- lint local ----------
// ktlint (formatação) e detekt (problemas de código) rodam como programas à parte, chamados pelo
// Gradle: assim não dependem de plugins que precisam acompanhar o AGP/KMP. Uso:
//   ./gradlew ktlintCheck     confere a formatação
//   ./gradlew ktlintFormat    corrige a formatação
//   ./gradlew detekt          procura problemas de código (config em config/detekt/detekt.yml;
//                             o que já existia está em config/detekt/baseline.xml)
//   ./gradlew checkAll        lint + testes + Android Lint + compilação comum do iOS

val ktlint: Configuration by configurations.creating
val detektCli: Configuration by configurations.creating

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes { attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL)) }
    }
    detektCli(libs.detekt.cli)
}

val kotlinSources = listOf("**/src/**/*.kt", "**/*.kts", "!**/build/**")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Confere a formatação do código Kotlin (ktlint)."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(kotlinSources)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Corrige a formatação do código Kotlin (ktlint)."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(listOf("-F") + kotlinSources)
}

val detektArgs = listOf(
    "--input",
    listOf("engine/src", "shared/src", "androidApp/src").joinToString(","),
    "--config",
    "config/detekt/detekt.yml",
    "--build-upon-default-config",
    "--excludes",
    "**/build/**",
)
val detektBaseline = "config/detekt/baseline.xml"

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Procura problemas de código (detekt). Só falha com problemas fora da baseline."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(detektArgs + listOf("--baseline", detektBaseline, "--report", "txt:build/reports/detekt.txt"))
}

tasks.register<JavaExec>("detektBaseline") {
    group = "verification"
    description = "Regrava a baseline do detekt com os problemas atuais (use só ao aceitar débito conscientemente)."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(detektArgs + listOf("--baseline", detektBaseline, "--create-baseline"))
}

tasks.register("checkAll") {
    group = "verification"
    description = "Tudo o que deve passar antes de um commit: lint, testes, Android Lint e iOS comum."
    dependsOn(
        "ktlintCheck",
        "detekt",
        ":engine:jvmTest",
        ":shared:testAndroidHostTest",
        ":shared:compileIosMainKotlinMetadata",
        ":androidApp:lintDebug",
    )
}
