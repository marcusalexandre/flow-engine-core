import java.util.Properties
import org.gradle.api.tasks.Exec

plugins {
    kotlin("multiplatform") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.android.library") version "8.2.2" apply false
    id("maven-publish")
}

val androidSdkDir: String? = sequenceOf(
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
    run {
        val localProps = file("local.properties")
        if (localProps.exists()) {
            val props = Properties()
            localProps.inputStream().use { props.load(it) }
            props.getProperty("sdk.dir")
        } else {
            null
        }
    }
).firstOrNull { !it.isNullOrBlank() }

val enableAndroid = (androidSdkDir != null && file(androidSdkDir).exists()) ||
    (project.findProperty("enableAndroid") as String?) == "true"

if (enableAndroid) {
    apply(plugin = "com.android.library")
}

group = "io.flowmobile"
version = (project.findProperty("versionOverride") as String?) ?: "0.1.1"

val npmScope = (project.findProperty("npmScope") as String?)
    ?: System.getenv("NPM_SCOPE")
    ?: "marcusalexandre"
val npmPackageName = "@${npmScope}/${project.name}"

kotlin {
    // JVM Target
    jvm {
        jvmToolchain(17)
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    
    // JS Target
    js(IR) {
        moduleName = project.name
        binaries.library()
        generateTypeScriptDefinitions()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        nodejs()

        compilations["main"].packageJson {
            name = npmPackageName
            version = project.version.toString()
            customField("types", "kotlin/${project.name}.d.ts")
            customField("module", "kotlin/${project.name}.js")
        }
    }
    
    // Android Target
    if (enableAndroid) {
        androidTarget {
            publishLibraryVariants("release")
        }
    }
    
    // iOS Targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "FlowEngineCore"
            isStatic = true
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.1")
            }
        }
        val jsMain by getting
        val jsTest by getting
        if (enableAndroid) {
            val androidMain by getting {
                dependencies {
                    implementation("androidx.core:core-ktx:1.12.0")
                }
            }
        }
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

if (enableAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension> {
        namespace = "io.flowmobile.core"
        compileSdk = 34
        defaultConfig {
            minSdk = 24
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

publishing {
    publications {
        // A publicação KMP cria automaticamente publicações para cada target
        // Configuração adicional se necessário
    }
    repositories {
        mavenLocal()
        
        // GitHub Packages
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/marcusalexandre/flow-engine-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String? ?: ""
            }
        }
    }
}

val jsPackageDir = layout.buildDirectory.dir("js/packages/${project.name}")

tasks.register<Exec>("publishJsToNpm") {
    dependsOn("jsProductionLibraryDistribution")
    doFirst {
        workingDir = jsPackageDir.get().asFile
    }
    commandLine("npm", "publish", "--access", "public")
}
