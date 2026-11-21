import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

data class LibrashaderAbiTarget(
    val abi: String,
    val rustTarget: String,
    val ndkLibTriple: String,
    val clangPrefix: String,
)

android {
    testBuildType = "debug"

    val stormSigningProps = Properties().apply {
        val centralProps = file("F:/STORM ALL PROJECTS/.storm-secrets/storm_signing.properties")
        val envProps = System.getenv("STORM_SIGNING_PROPS")?.let { file(it) }
        val activePropsFile = listOfNotNull(envProps, centralProps).firstOrNull { it.exists() }
        if (activePropsFile != null) {
            activePropsFile.inputStream().use { load(it) }
        }
    }
    val localProps = gradleLocalProperties(rootDir, providers)
    val unifiedCertFile = (
        System.getenv("STORM_CERT_FILE")
            ?: localProps.getProperty("STORM_CERT_FILE")
            ?: stormSigningProps.getProperty("STORM_CERT_FILE")
        )?.let { file(it) }?.takeIf { it.exists() }
    val unifiedCertPassword = System.getenv("STORM_CERT_PASSWORD")
        ?: localProps.getProperty("STORM_CERT_PASSWORD")
        ?: stormSigningProps.getProperty("STORM_CERT_PASSWORD")
        ?: ""
    val unifiedKeyAlias = System.getenv("STORM_KEY_ALIAS")
        ?: localProps.getProperty("STORM_KEY_ALIAS")
        ?: stormSigningProps.getProperty("STORM_KEY_ALIAS")
        ?: ""
    val unifiedKeyPassword = System.getenv("STORM_KEY_PASSWORD")
        ?: localProps.getProperty("STORM_KEY_PASSWORD")
        ?: stormSigningProps.getProperty("STORM_KEY_PASSWORD")
        ?: ""

    signingConfigs {
        create("release") {
            if (unifiedCertFile != null) {
                storeFile = unifiedCertFile
                storePassword = unifiedCertPassword
                keyAlias = unifiedKeyAlias
                keyPassword = unifiedKeyPassword
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        getByName("debug") {
            if (unifiedCertFile != null) {
                storeFile = unifiedCertFile
                storePassword = unifiedCertPassword
                keyAlias = unifiedKeyAlias
                keyPassword = unifiedKeyPassword
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    namespace = "com.storm3ds.emulator"
    compileSdk = AppConfig.compileSdkVersion
    ndkVersion = AppConfig.ndkVersion
    defaultConfig {
        applicationId = "com.storm3ds.emulator"
        minSdk = AppConfig.minSdkVersion
        targetSdk = AppConfig.targetSdkVersion
        versionCode = AppConfig.versionCode
        versionName = AppConfig.versionName
        manifestPlaceholders["appName"] = "@string/app_name"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        val jniDirPath = "${project.projectDir.absolutePath.replace('\\', '/')}/src/main/cpp/jni"
        val settingKeysPath = "$jniDirPath/jni_setting_keys.cpp.in"

        externalNativeBuild {
            cmake {
                arguments(
                    "-DENABLE_LTO=OFF",
                    "-DENABLE_QT=OFF",
                    "-DENABLE_QT_TRANSLATION=OFF",
                    "-DENABLE_SDL2=OFF",
                    "-DENABLE_OPENAL=OFF",
                    "-DENABLE_LIBUSB=OFF",
                    "-DENABLE_DISCORD_RPC=OFF",
                    "-DENABLE_TESTS=OFF",
                    "-DENABLE_CUBEB=ON",
                    "-DENABLE_VULKAN=ON",
                    "-DENABLE_OPENGL=ON",
                    "-DENABLE_WEB_SERVICE=ON",
                    "-DCITRA_WARNINGS_AS_ERRORS=OFF",
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DSTORM_ANDROID_JNI_DIR=$jniDirPath",
                    "-DSTORM_JNI_SETTING_KEYS_IN=$settingKeysPath",
                    "-DCMAKE_BUILD_PARALLEL_LEVEL=4",
                )
                targets("storm3ds")
            }
        }
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        viewBinding = true
        compose = true
        resValues = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
        }
    }

    flavorDimensions += listOf("version", "build")
    productFlavors {
        create("legacy") {
            dimension = "version"
            resValue("bool", "adrenotools_enabled", "false")
            externalNativeBuild {
                cmake {
                    arguments("-DSTORM_ENABLE_ADRENOTOOLS=OFF")
                }
            }
        }
        create("gitHub") {
            dimension = "version"
            isDefault = true
            resValue("bool", "adrenotools_enabled", "true")
            externalNativeBuild {
                cmake {
                    arguments("-DSTORM_ENABLE_ADRENOTOOLS=ON")
                }
            }
        }
        create("prod") {
            dimension = "build"
            isDefault = true
        }
        create("nightly") {
            dimension = "build"
            applicationIdSuffix = ".nightly"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../core/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories += "$projectDir/schemas"
        getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/librashader/jniLibs"))
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
            excludes += "**/libVkLayer_khronos_validation.so"
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants(selector().withName("gitHubProdDebug")) { variant ->
        variant.manifestPlaceholders.put("appName", "debug STORM 3DS")
    }
}

val librashaderRepoUrl = "https://github.com/SnowflakePowered/librashader.git"
val librashaderPinnedRevision = "76462c030b75c4f2d56e5386c3d4d7d1128318b8"
val librashaderCargoFeatures = "runtime-opengl,runtime-vulkan,stable"
val librashaderPatchDir = layout.projectDirectory.dir("librashader-patches")
val librashaderSourceDir = layout.buildDirectory.dir("librashader/src")
val librashaderOutputDir = layout.buildDirectory.dir("generated/librashader")
val librashaderSourceRevisionFile = librashaderSourceDir.map { it.file(".storm3ds-librashader-revision") }
val librashaderRevisionFile = librashaderOutputDir.map { it.file("REVISION") }
val librashaderAbiTargets = listOf(
    LibrashaderAbiTarget(
        abi = "arm64-v8a",
        rustTarget = "aarch64-linux-android",
        ndkLibTriple = "aarch64-linux-android",
        clangPrefix = "aarch64-linux-android",
    ),
    LibrashaderAbiTarget(
        abi = "x86_64",
        rustTarget = "x86_64-linux-android",
        ndkLibTriple = "x86_64-linux-android",
        clangPrefix = "x86_64-linux-android",
    ),
)

fun librashaderPrebuiltReady(): Boolean {
    val revFile = librashaderRevisionFile.get().asFile
    if (!revFile.exists() || revFile.readText().trim() != librashaderPinnedRevision) {
        return false
    }
    return librashaderAbiTargets.all { target ->
        librashaderOutputDir.get().file("jniLibs/${target.abi}/liblibrashader.so").asFile.exists()
    } && librashaderOutputDir.get().file("include/librashader.h").asFile.exists()
}

fun librashaderToolSearchDirs(): List<File> {
    val pathDirs = System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.filter(String::isNotBlank)
        ?.map(::File)
        ?: emptyList()
    val homeDir = System.getProperty("user.home")?.takeIf(String::isNotBlank)?.let(::File)
    return (pathDirs + listOfNotNull(
        homeDir?.resolve(".cargo/bin"),
        file("/opt/homebrew/bin"),
        file("/usr/local/bin"),
        file("/usr/bin"),
    )).distinctBy(File::getAbsolutePath)
}

fun augmentedLibrashaderPath(): String =
    librashaderToolSearchDirs().joinToString(File.pathSeparator, transform = File::getAbsolutePath)

val isWindowsHost = System.getProperty("os.name").lowercase().contains("windows")

fun resolveBuildTool(tool: String): String {
    val exeNames = if (isWindowsHost) {
        listOf("$tool.exe", "$tool.cmd", "$tool.bat", tool)
    } else {
        listOf(tool)
    }
    for (dir in librashaderToolSearchDirs()) {
        for (exeName in exeNames) {
            val candidate = dir.resolve(exeName)
            if (candidate.isFile && (!isWindowsHost || candidate.extension.isNotEmpty()) && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }
    }
    return tool
}

fun androidNdkHostTag(): String {
    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        osName.contains("windows") -> "windows-x86_64"
        osName.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm64")) "darwin-arm64" else "darwin-x86_64"
        else -> "linux-x86_64"
    }
}

fun rustTargetEnvKey(rustTarget: String): String =
    rustTarget.uppercase().replace('-', '_')

fun resolveAndroidNdkHome(): File {
    val localProperties = gradleLocalProperties(rootDir, providers)
    val sdkDir = localProperties.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
    val explicitNdk = System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_NDK_ROOT")
    return listOfNotNull(
        explicitNdk?.takeIf(String::isNotBlank)?.let(::File),
        sdkDir?.takeIf(String::isNotBlank)?.let { File(it, "ndk/${AppConfig.ndkVersion}") },
    ).firstOrNull(File::isDirectory)
        ?: error("Unable to locate Android NDK ${AppConfig.ndkVersion} for librashader build.")
}

val prepareLibrashaderSource by tasks.registering {
    group = "build"
    description = "Fetches pinned librashader source and applies patches if not prebuilt."
    onlyIf { !librashaderPrebuiltReady() }
    inputs.property("librashaderRepoUrl", librashaderRepoUrl)
    inputs.property("librashaderPinnedRevision", librashaderPinnedRevision)
    inputs.dir(librashaderPatchDir)
    outputs.file(librashaderSourceRevisionFile)

    doLast {
        val sourceDir = librashaderSourceDir.get().asFile
        val patchFiles = librashaderPatchDir.asFileTree.matching {
            include("*.patch")
        }.files.sortedBy(File::getName)
        val expectedStamp = buildString {
            appendLine(librashaderPinnedRevision)
            for (patchFile in patchFiles) {
                appendLine("${patchFile.name}:${patchFile.length()}")
            }
        }
        val stampFile = librashaderSourceRevisionFile.get().asFile
        if (stampFile.exists() && stampFile.readText() == expectedStamp) {
            return@doLast
        }
        sourceDir.deleteRecursively()
        sourceDir.parentFile.mkdirs()

        fun runGit(workingDir: File, vararg args: String) {
            val process = ProcessBuilder("git", *args)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("git ${args.joinToString(" ")} failed with exit code $exitCode:\n$output")
            }
        }

        runGit(sourceDir.parentFile, "clone", "--filter=blob:none", librashaderRepoUrl, sourceDir.name)
        runGit(sourceDir, "checkout", "--detach", librashaderPinnedRevision)
        for (patchFile in patchFiles) {
            runGit(sourceDir, "apply", "--whitespace=nowarn", patchFile.absolutePath)
        }
        stampFile.writeText(expectedStamp)
    }
}

val copyLibrashaderHeaders by tasks.registering(Copy::class) {
    group = "build"
    description = "Copies librashader C API headers when building from source."
    onlyIf { !librashaderPrebuiltReady() }
    dependsOn(prepareLibrashaderSource)
    from(librashaderSourceDir.map { it.dir("include") })
    include("librashader.h", "librashader_ld.h")
    into(librashaderOutputDir.map { it.dir("include") })
}

val copyLibrashaderAbiArtifacts = librashaderAbiTargets.map { abiTarget ->
    val capitalizedAbi = abiTarget.abi
        .split('-', '_')
        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    val installRustTarget = tasks.register<Exec>("installLibrashader${capitalizedAbi}RustTarget") {
        group = "build"
        description = "Installs Rust target ${abiTarget.rustTarget} for librashader."
        onlyIf { !librashaderPrebuiltReady() }
        dependsOn(prepareLibrashaderSource)
        commandLine(resolveBuildTool("rustup"), "target", "add", abiTarget.rustTarget)
        environment("PATH", augmentedLibrashaderPath())
    }
    val compileLibrashader = tasks.register<Exec>("compileLibrashader${capitalizedAbi}") {
        group = "build"
        description = "Compiles librashader for ${abiTarget.abi}."
        onlyIf { !librashaderPrebuiltReady() }
        dependsOn(prepareLibrashaderSource, installRustTarget)

        val hostTag = androidNdkHostTag()
        val targetEnvKey = rustTargetEnvKey(abiTarget.rustTarget)
        val ndkHome = resolveAndroidNdkHome()
        val toolchain = ndkHome.resolve("toolchains/llvm/prebuilt/$hostTag/bin")
        val clangSuffix = if (isWindowsHost) ".cmd" else ""
        val exeSuffix = if (isWindowsHost) ".exe" else ""
        val clang = toolchain.resolve("${abiTarget.clangPrefix}${AppConfig.minSdkVersion}-clang$clangSuffix")
        val clangCpp = toolchain.resolve("${abiTarget.clangPrefix}${AppConfig.minSdkVersion}-clang++$clangSuffix")
        val llvmAr = toolchain.resolve("llvm-ar$exeSuffix")

        inputs.property("librashaderPinnedRevision", librashaderPinnedRevision)
        inputs.property("librashaderCargoFeatures", librashaderCargoFeatures)
        inputs.dir(librashaderPatchDir)
        outputs.file(librashaderSourceDir.map {
            it.file("target/${abiTarget.rustTarget}/optimized/liblibrashader_capi.so")
        })

        workingDir = librashaderSourceDir.get().asFile
        commandLine(
            resolveBuildTool("cargo"),
            "+stable",
            "build",
            "--package",
            "librashader-capi",
            "--profile",
            "optimized",
            "--target",
            abiTarget.rustTarget,
            "--no-default-features",
            "--features",
            librashaderCargoFeatures,
        )
        environment("CARGO_BUILD_JOBS", "3")
        environment("CC_${abiTarget.rustTarget.replace("-", "_")}", clang.absolutePath)
        environment("CXX_${abiTarget.rustTarget.replace("-", "_")}", clangCpp.absolutePath)
        environment("AR_${abiTarget.rustTarget.replace("-", "_")}", llvmAr.absolutePath)
        environment("CARGO_TARGET_${targetEnvKey}_LINKER", clang.absolutePath)
        environment("CARGO_TARGET_${targetEnvKey}_RUSTFLAGS", "-C link-arg=-Wl,-soname,liblibrashader.so -C opt-level=3 -C codegen-units=1 -C panic=abort -C strip=symbols")
        environment("PATH", augmentedLibrashaderPath())
    }

    tasks.register<Copy>("copyLibrashader${capitalizedAbi}Artifacts") {
        group = "build"
        description = "Copies librashader shared library for ${abiTarget.abi}."
        onlyIf { !librashaderPrebuiltReady() }
        dependsOn(compileLibrashader)
        from(librashaderSourceDir.map {
            it.file("target/${abiTarget.rustTarget}/optimized/liblibrashader_capi.so")
        }) {
            rename { "liblibrashader.so" }
        }
        into(librashaderOutputDir.map { it.dir("jniLibs/${abiTarget.abi}") })
    }
}

val buildLibrashaderAndroid by tasks.registering {
    group = "build"
    description = "Ensures librashader C API libraries and headers are prepared for STORM 3DS."
    dependsOn(copyLibrashaderHeaders, *copyLibrashaderAbiArtifacts.toTypedArray())
    outputs.file(librashaderRevisionFile)

    doLast {
        val revOut = librashaderRevisionFile.get().asFile
        revOut.parentFile.mkdirs()
        revOut.writeText("$librashaderPinnedRevision\n")
    }
}

tasks.named("preBuild").configure {
    dependsOn(buildLibrashaderAndroid)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(projects.masterswitch)
    implementation(projects.rcheevosApi)
    implementation(projects.common)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.rxjava)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.window)
    implementation(libs.androidx.work)
    implementation(libs.android.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil)
    implementation(libs.gson)
    implementation(libs.hilt)
    implementation(libs.kotlin.serialization)
    implementation(libs.kotlin.serialization.protobuf)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.rx)
    implementation(libs.picasso)
    implementation(libs.markwon)
    implementation(libs.markwon.imagepicasso)
    implementation(libs.markwon.linkify)
    implementation(libs.commons.compress)
    implementation(libs.junrar)
    implementation(libs.xz)
    implementation(libs.okhttp)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.translate)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)

    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.android)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.register("renameReleaseApk") {
    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/gitHubProd/release").get().asFile
        val defaultApk = File(releaseDir, "app-gitHubProd-release.apk")
        val altApk = File(releaseDir, "app-gitHub-prod-release.apk")
        val sourceApk = listOf(defaultApk, altApk).firstOrNull { it.exists() }
        val targetApk = File(releaseDir, "STORM_3DS_${AppConfig.versionName}.apk")
        val releasesDir = rootDir.resolve("releases").apply { mkdirs() }
        val persistentApk = File(releasesDir, "STORM_3DS_${AppConfig.versionName}.apk")
        if (sourceApk != null) {
            sourceApk.copyTo(targetApk, overwrite = true)
            sourceApk.copyTo(persistentApk, overwrite = true)
        }
    }
}

tasks.matching { it.name == "assembleGitHubProdRelease" }.configureEach {
    finalizedBy("renameReleaseApk")
}

tasks.register("renameLegacyReleaseApk") {
    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/legacyProd/release").get().asFile
        val defaultApk = File(releaseDir, "app-legacyProd-release.apk")
        val altApk = File(releaseDir, "app-legacy-prod-release.apk")
        val sourceApk = listOf(defaultApk, altApk).firstOrNull { it.exists() }
        val targetApk = File(releaseDir, "STORM_3DS_${AppConfig.versionName}_LEGACY.apk")
        val releasesDir = rootDir.resolve("releases").apply { mkdirs() }
        val persistentApk = File(releasesDir, "STORM_3DS_${AppConfig.versionName}_LEGACY.apk")
        if (sourceApk != null) {
            sourceApk.copyTo(targetApk, overwrite = true)
            sourceApk.copyTo(persistentApk, overwrite = true)
        }
    }
}

tasks.matching { it.name == "assembleLegacyProdRelease" }.configureEach {
    finalizedBy("renameLegacyReleaseApk")
}
