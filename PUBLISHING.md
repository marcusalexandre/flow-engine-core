# Publicação da Biblioteca

## Publicar no npm (JavaScript)

Pré-requisitos:
- Node.js 18+ instalado
- Token npm com permissão de publish (defina `NPM_TOKEN`)
- Opcional: `NPM_SCOPE` ou `-PnpmScope=seu-scope`

```bash
# Gera o pacote JS (inclui typings .d.ts)
./gradlew jsProductionLibraryDistribution

# Publica no npm (usa o pacote em build/js/packages/flow-engine-core)
./gradlew publishJsToNpm
```

> O nome do pacote é gerado como `@<scope>/flow-engine-core`.

## Publicar no Maven Local

Para publicar a biblioteca no repositório Maven Local (`~/.m2/repository`):

```bash
./gradlew publishJvmPublicationToMavenLocal publishKotlinMultiplatformPublicationToMavenLocal publishJsPublicationToMavenLocal
```

Ou de forma simplificada (publica todos os targets disponíveis):
```bash
./gradlew publishToMavenLocal
```

> **Nota**: A publicação completa com `publishToMavenLocal` requer Android SDK configurado. Se não tiver o SDK, use o comando específico acima que publica apenas JVM, JS e metadata.

## Usar em Outros Projetos

### 1. Adicionar o repositório Maven Local

No `build.gradle.kts` do projeto que vai consumir a biblioteca:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

### 2. Adicionar a dependência

#### Em projetos Kotlin Multiplatform:

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.flowmobile:flow-engine-core:0.1.0")
            }
        }
    }
}
```

#### Em projetos JVM/Android:

```kotlin
dependencies {
    implementation("io.flowmobile:flow-engine-core-jvm:0.1.0")
}
```

#### Em projetos JS:

```kotlin
dependencies {
    implementation("io.flowmobile:flow-engine-core-js:0.1.0")
}
```

## Localização dos Artefatos

Após a publicação, os artefatos estarão disponíveis em:
```
~/.m2/repository/io/flowmobile/flow-engine-core/0.1.0/
```

Arquivos publicados:
- `flow-engine-core-0.1.0.jar` - Artefato principal (metadata)
- `flow-engine-core-0.1.0.module` - Gradle Module Metadata
- `flow-engine-core-0.1.0.pom` - Maven POM
- `flow-engine-core-0.1.0-sources.jar` - Código-fonte
- `flow-engine-core-jvm-0.1.0.jar` - Target JVM
- `flow-engine-core-js-0.1.0.jar` - Target JS

## Atualizar Versão

Para publicar uma nova versão, atualize o `version` no `build.gradle.kts`:

```kotlin
group = "io.flowmobile"
version = "0.2.0"  // Nova versão
```

E execute novamente o comando de publicação.

## Publicar em Repositório Remoto

Para publicar em um repositório Maven remoto (Nexus, Artifactory, etc.), adicione no `build.gradle.kts`:

```kotlin
publishing {
    repositories {
        maven {
            name = "CustomRepo"
            url = uri("https://seu-repositorio.com/maven")
            credentials {
                username = project.findProperty("repoUsername") as String? ?: ""
                password = project.findProperty("repoPassword") as String? ?: ""
            }
        }
    }
}
```

E execute:
```bash
./gradlew publishAllPublicationsToCustomRepoRepository
```
