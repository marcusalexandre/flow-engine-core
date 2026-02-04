# Flow Engine Core

Motor de execução multiplatforma de fluxos de trabalho grafo-baseados construído com Kotlin Multiplatform. Núcleo compartilhado para Android, iOS, Web e servidor com arquitetura imutável, totalmente tipada e serializada.

## 🎯 Visão Geral

O **Flow Engine Core** é o coração da FlowMobile Platform, fornecendo:

- ✅ Modelo de domínio imutável para fluxos
- ✅ Compilação para múltiplas plataformas (JVM, JS/IR, Android, iOS)
- ✅ Validação rigorosa de fluxos
- ✅ Contexto de execução tipado
- ✅ Suporte a expressões e variáveis
- ✅ Serialização JSON nativa
- ✅ Arquitetura Domain-Driven Design
- ✅ 95%+ cobertura de testes

## 📋 Status do Projeto

**Fase Atual:** Phase 1 - Fundação do Domínio ✓ (Completo)

Este módulo implementa o modelo fundamental de domínio para a plataforma FlowMobile, servindo como base compartilhada para todos os clientes (Android, iOS, Web) e servidor.

## ✨ Features Implementadas

### Phase 1: Fundação de Domínio (Completo)

#### Modelo de Domínio
- ✅ **Estruturas Imutáveis** - Dados nunca mudam após criação
- ✅ **Tipos Seguros** - Tipagem forte em tempo de compilação
- ✅ **Entidades Serializáveis** - JSON nativo via kotlinx.serialization
- ✅ **Validação em Construtor** - Falha-rápido (fail-fast)
- ✅ **Conversão de Tipos** - Conversão automática de tipos de porta

#### Componentes Base
| Componente | Descrição | Propriedades | Ports | Versão |
|-----------|-----------|-------------|-------|---------|
| **StartComponent** | Ponto único de entrada | name, description | 0 in, 1 out | 1.0 |
| **EndComponent** | Ponto(s) de saída | name, description, isError | 1 in, 0 out | 1.0 |
| **DecisionComponent** | Avaliação condicional | name, condition, timeout | 1 in, N out | 1.0 |
| **ActionComponent** | Executa serviço Host | service, method, inputs, timeout | 1+ in, 2+ out | 1.0 |

#### Entidades Principais
```
Flow (id, name, version, components, connections, metadata)
├── Component[] (startComponent, endComponents, actionComponents, decisionComponents)
│   ├── id: String (UUID)
│   ├── name: String
│   ├── properties: Map<String, ComponentProperty>
│   └── ports: Map<String, Port>
├── Connection[] (sourceComponent → targetComponent)
│   ├── id: String
│   ├── sourceComponentId, sourcePortId
│   ├── targetComponentId, targetPortId
│   └── metadata: Map<String, Any>
├── Port (define pontos de conexão)
│   ├── id: String
│   ├── name: String
│   ├── direction: INPUT | OUTPUT
│   ├── portType: PortType (ANY, OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL)
│   └── multiple: Boolean (permite múltiplas conexões)
└── ExecutionContext (estado durante execução)
    ├── flowId, executionId, currentComponentId
    ├── variables: Map<String, VariableValue>
    ├── executionPath: List<ComponentId>
    ├── startTime, elapsedTime
    └── status: CREATED | EXECUTING | COMPLETED | FAILED
```

#### Sistema de Tipos
```kotlin
PortType:
  - ANY          (aceita qualquer tipo)
  - OBJECT       (JSON object)
  - ARRAY        (array de valores)
  - STRING       (texto)
  - NUMBER       (int, long, float, double)
  - BOOLEAN      (true/false)
  - NULL         (nulo)

ComponentProperty:
  - StringValue
  - NumberValue
  - BooleanValue
  - ObjectValue
  - ArrayValue
  - NullValue

VariableValue: (valores em tempo de execução)
  - StringValue
  - NumberValue
  - BooleanValue
  - ObjectValue
  - ArrayValue
  - NullValue
```

#### Validação Integrada
- ✅ **Estrutural** - Exatamente 1 START, 1+ END, sem componentes órfãos
- ✅ **Conexões** - Sem auto-loops, portas válidas, tipos compatíveis
- ✅ **Grafo** - Sem ciclos, todos componentes alcançáveis de START
- ✅ **Propriedades** - Campos obrigatórios, tipos de dados válidos
- ✅ **Expressões** - Sintaxe válida em condições (Phase 2)
- ✅ **IDs Únicos** - Componentes e conexões com IDs únicos

### Phase 2: Runtime e Execução (Planejado)

- ⏳ Graph Interpreter
- ⏳ Flow Executor
- ⏳ Audit Trail
- ⏳ Rollback/Resume Engine
- ⏳ Timeout Handling

### Phase 3: Carregamento e Validação JSON (Planejado)

- ⏳ JSON Schema Definition
- ⏳ Flow Loader
- ⏳ Parser de Expressões
- ⏳ Validador Avançado
- ⏳ Versionamento de Schema

## 🎯 Suporte Multiplataforma

O Flow Engine Core compila para múltiplas plataformas com código fonte único:

| Plataforma | Target | Versão | Status |
|-----------|--------|--------|--------|
| **JVM** | Java Bytecode | Java 17+ | ✅ Ativo |
| **JavaScript** | ES2015+ | Node.js 18+ | ✅ Ativo |
| **Android** | Native | API 24+ | ✅ Ativo |
| **iOS** | Native | iOS 13+ | ✅ Ativo |

### Compilação Kotlin Multiplatform

```
commonMain/
├── kotlin/
│   └── io.flowmobile.core/
│       ├── domain/                    # Código compartilhado
│       │   ├── Flow.kt
│       │   ├── Component.kt
│       │   ├── Connection.kt
│       │   ├── Port.kt
│       │   ├── ExecutionContext.kt
│       │   ├── VariableValue.kt
│       │   └── ComponentProperty.kt
│       └── extensions/               # Extensões comuns
│           └── FlowExtensions.kt
├── jvmMain/                          # Código específico JVM
│   └── kotlin/io.flowmobile.core/
│       ├── jvm/
│       │   └── JvmExecutor.kt
│       └── platform/
│           └── PlatformContext.kt
├── jsMain/                           # Código específico JS
│   └── kotlin/io.flowmobile.core/
│       └── platform/
│           └── PlatformContext.kt
├── androidMain/                      # Código específico Android
│   └── kotlin/io.flowmobile.core/
│       └── platform/
│           └── AndroidExecutor.kt
└── iosMain/                          # Código específico iOS
    └── kotlin/io.flowmobile.core/
        └── platform/
            └── IosExecutor.kt
```

## 📦 Stack Técnico

- **Kotlin**: 1.9.0+ (type-safe language)
- **Gradle**: 8.1+ (build system)
- **kotlinx.serialization**: JSON nativo
- **Kotlin Multiplatform**: KMP (multiplatform compilation)
- **JUnit**: Testes (JVM)
- **Kotlin Test**: Testes (todos platforms)

## 🏗️ Estrutura do Projeto

```
flow-engine-core/
├── build.gradle.kts                 # Build multiplatform
├── gradle.properties
├── settings.gradle.kts
├── local.properties
├── PUBLISHING.md                    # Guia de publicação
├── README.md
├── src/
│   ├── commonMain/
│   │   └── kotlin/
│   │       └── io/flowmobile/core/
│   │           ├── domain/
│   │           │   ├── Flow.kt
│   │           │   │   - id: String
│   │           │   │   - name: String
│   │           │   │   - version: String
│   │           │   │   - components: List<Component>
│   │           │   │   - connections: List<Connection>
│   │           │   │   - validate(): Result<Unit>
│   │           │   │   - getComponentById(id): Component?
│   │           │   │   - getConnections(componentId): List<Connection>
│   │           │   ├── Component.kt (interface)
│   │           │   │   ├── StartComponent.kt
│   │           │   │   ├── EndComponent.kt
│   │           │   │   ├── ActionComponent.kt
│   │           │   │   └── DecisionComponent.kt
│   │           │   ├── Connection.kt
│   │           │   │   - id: String
│   │           │   │   - sourceComponentId: String
│   │           │   │   - sourcePortId: String
│   │           │   │   - targetComponentId: String
│   │           │   │   - targetPortId: String
│   │           │   │   - validate(): Boolean
│   │           │   ├── Port.kt
│   │           │   │   - id: String
│   │           │   │   - name: String
│   │           │   │   - direction: PortDirection
│   │           │   │   - portType: PortType
│   │           │   │   - multiple: Boolean
│   │           │   ├── ExecutionContext.kt
│   │           │   │   - flowId, executionId
│   │           │   │   - variables: Map<String, VariableValue>
│   │           │   │   - withVariable(name, value): ExecutionContext
│   │           │   │   - getVariable(name): VariableValue?
│   │           │   ├── ExecutionResult.kt
│   │           │   │   - flowId, executionId
│   │           │   │   - success: Boolean
│   │           │   │   - outputs: Map<String, VariableValue>
│   │           │   │   - error: String?
│   │           │   │   - timeline: List<TimelineEvent>
│   │           │   ├── VariableValue.kt
│   │           │   │   ├── StringValue
│   │           │   │   ├── NumberValue
│   │           │   │   ├── BooleanValue
│   │           │   │   ├── ObjectValue
│   │           │   │   ├── ArrayValue
│   │           │   │   └── NullValue
│   │           │   ├── ComponentProperty.kt
│   │           │   │   └── (mesmos tipos acima)
│   │           │   ├── PortType.kt (enum)
│   │           │   ├── PortDirection.kt (INPUT, OUTPUT)
│   │           │   ├── ExecutionStatus.kt (enum)
│   │           │   └── TimelineEvent.kt
│   │           ├── exceptions/
│   │           │   ├── FlowException.kt
│   │           │   ├── InvalidFlowException.kt
│   │           │   ├── ComponentNotFoundException.kt
│   │           │   └── InvalidConnectionException.kt
│   │           ├── extensions/
│   │           │   ├── FlowExtensions.kt
│   │           │   │   - Flow.findPath()
│   │           │   │   - Flow.topologicalSort()
│   │           │   │   - Flow.hasCycle()
│   │           │   └── ComponentExtensions.kt
│   │           └── serialization/
│   │               ├── FlowSerializer.kt
│   │               └── VariableValueSerializer.kt
│   ├── commonTest/
│   │   └── kotlin/
│   │       └── io/flowmobile/core/
│   │           ├── domain/
│   │           │   ├── FlowTest.kt
│   │           │   ├── ComponentTest.kt
│   │           │   ├── ConnectionTest.kt
│   │           │   ├── ExecutionContextTest.kt
│   │           │   └── ValidationTest.kt
│   │           └── serialization/
│   │               └── SerializationTest.kt
│   ├── jvmMain/
│   │   └── kotlin/
│   │       └── io/flowmobile/core/
│   │           ├── jvm/
│   │           │   ├── JvmExecutor.kt
│   │           │   └── JvmContext.kt
│   │           └── platform/
│   │               └── PlatformContext.kt
│   ├── jvmTest/
│   │   └── kotlin/
│   │       └── io/flowmobile/core/
│   │           └── jvm/
│   │               └── JvmExecutorTest.kt
│   ├── jsMain/
│   │   └── kotlin/
│   │       └── io/flowmobile/core/
│   │           └── platform/
│   │               └── PlatformContext.kt
│   ├── jsTest/
│   │   └── kotlin/
│   │       └── io/flowmobile/core/
│   │           └── js/
│   │               └── JsInteropTest.kt
│   ├── androidMain/
│   │   └── kotlin/
│   │       └── io/flowmobile/core/
│   │           └── platform/
│   │               ├── PlatformContext.kt
│   │               └── AndroidExecutor.kt
│   └── iosMain/
│       └── kotlin/
│           └── io/flowmobile/core/
│               └── platform/
│                   └── IosExecutor.kt
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build/
│   ├── classes/
│   ├── generated/
│   ├── kotlin/
│   ├── outputs/
│   └── reports/
└── kotlin-js-store/
```

## 🚀 Compilação e Setup

### Pré-requisitos

```bash
# JDK 17 ou superior
java -version

# Gradle 8.1+ (incluído via wrapper)
./gradlew --version

# Kotlin 1.9.0+
# (gerenciado pelo Gradle)
```

> Android é opcional: o target Android só é habilitado quando o SDK está disponível via
> `ANDROID_HOME`/`ANDROID_SDK_ROOT` ou com `-PenableAndroid=true`. Não é necessário manter
> `local.properties` no repositório.
>
> Exemplo: copie [local.properties.example](local.properties.example) para `local.properties`
> e ajuste o caminho do SDK, se desejar habilitar Android localmente.

### Compilar para Todos os Targets

```bash
# Limpar compilações anteriores
./gradlew clean

# Compilar tudo (JVM, JS, Android, iOS)
./gradlew build

# Apenas build sem testes (rápido)
./gradlew assemble

# Build com verbose output
./gradlew build -v
```

### Compilar Targets Específicos

```bash
# JVM Target
./gradlew jvmJar

# JavaScript Target
./gradlew jsBrowserDevelopmentWebpack

# Android Target (requer Android SDK)
./gradlew androidDebug

# iOS Target (requer Xcode)
./gradlew iosSimulatorArm64Binaries
```

### Executar Testes

```bash
# Todos os testes
./gradlew test

# Testes JVM apenas
./gradlew jvmTest

# Testes JS apenas
./gradlew jsTest

# Testes com cobertura
./gradlew test --info

# Watch mode (recompila ao detectar mudanças)
./gradlew test --continuous
```

## 🌐 Uso no Web (JS/TS) via npm

O pacote JS é publicado como npm package e pode ser consumido em apps Vite/React.

### Instalação

```bash
npm install @<owner>/flow-engine-core
```

### Exemplo (JS/TS)

```ts
import { FlowEngineJs, JsHostServiceRegistry } from "@<owner>/flow-engine-core";

const engine = new FlowEngineJs();
const services = new JsHostServiceRegistry();

services.register("logger", (method, paramsJson) => {
    const params = JSON.parse(paramsJson);
    console.log("logger:", method, params);
    return JSON.stringify("ok");
});

const flowJson = JSON.stringify({
    schemaVersion: "1.0.0",
    flow: {
        id: "flow-1",
        name: "Hello Flow",
        version: "1.0.0",
        components: [
            { id: "start", type: "START", name: "Start" },
            {
                id: "action",
                type: "ACTION",
                name: "Log",
                properties: { service: "logger", method: "log" }
            },
            { id: "end", type: "END", name: "End" }
        ],
        connections: [
            {
                id: "c1",
                source: { componentId: "start", portId: "out" },
                target: { componentId: "action", portId: "in" }
            },
            {
                id: "c2",
                source: { componentId: "action", portId: "success" },
                target: { componentId: "end", portId: "in" }
            }
        ]
    }
});

const validation = engine.validate(flowJson);
if (!validation.isValid) {
    console.error(validation.errors);
} else {
    const resultJson = await engine.execute(flowJson, services);
    const result = JSON.parse(resultJson);
    console.log("result:", result);
}
```

### Sobre o JSON de serviços

- O `paramsJson` recebido pelo handler é um objeto JSON com valores simples
    (string, number, boolean, array, objeto, null).
- O retorno do handler deve ser um JSON string representando um valor simples
    (ex: `"ok"`, `123`, `{ "foo": true }`) ou `null`.

## 📦 Publicação npm (JS)

```bash
# Gerar pacote JS (inclui .d.ts)
./gradlew jsProductionLibraryDistribution

# Publicar no npm (precisa de NPM_TOKEN)
./gradlew publishJsToNpm
```

> Para publicar em outro escopo, defina `NPM_SCOPE` ou use `-PnpmScope=seu-scope`.

## 💻 Exemplo de Uso

### Criando um Fluxo Simples

```kotlin
import io.flowmobile.core.domain.*
import io.flowmobile.core.domain.components.*

// 1. Criar componentes
val start = StartComponent(
    id = "start-1",
    name = "Início do Fluxo"
)

val getUser = ActionComponent(
    id = "action-get-user",
    name = "Buscar Usuário",
    properties = mapOf(
        "service" to ComponentProperty.StringValue("userService"),
        "method" to ComponentProperty.StringValue("getById"),
        "inputs" to ComponentProperty.ObjectValue(mapOf(
            "userId" to ComponentProperty.StringValue("{userId}")
        ))
    )
)

val isAdult = DecisionComponent(
    id = "decision-1",
    name = "Maior de idade?",
    properties = mapOf(
        "condition" to ComponentProperty.StringValue("user.age >= 18")
    )
)

val endSuccess = EndComponent(
    id = "end-success",
    name = "Sucesso"
)

val endFailure = EndComponent(
    id = "end-failure",
    name = "Erro",
    properties = mapOf(
        "isError" to ComponentProperty.BooleanValue(true)
    )
)

// 2. Criar conexões
val connections = listOf(
    Connection(
        id = "conn-1",
        sourceComponentId = "start-1",
        sourcePortId = "output",
        targetComponentId = "action-get-user",
        targetPortId = "input"
    ),
    Connection(
        id = "conn-2",
        sourceComponentId = "action-get-user",
        sourcePortId = "success",
        targetComponentId = "decision-1",
        targetPortId = "input"
    ),
    Connection(
        id = "conn-3",
        sourceComponentId = "decision-1",
        sourcePortId = "true",
        targetComponentId = "end-success",
        targetPortId = "input"
    ),
    Connection(
        id = "conn-4",
        sourceComponentId = "decision-1",
        sourcePortId = "false",
        targetComponentId = "end-failure",
        targetPortId = "input"
    )
)

// 3. Criar e validar fluxo
val flow = Flow(
    id = "user-age-check",
    name = "Verificação de Maioridade",
    version = "1.0.0",
    components = listOf(start, getUser, isAdult, endSuccess, endFailure),
    connections = connections
)

// Validação automática no construtor
// Lança InvalidFlowException se houver problemas

// 4. Preparar contexto de execução
var context = ExecutionContext(
    flowId = flow.id,
    executionId = "exec-${System.currentTimeMillis()}"
)

// 5. Adicionar variáveis iniciais
context = context.withVariable("userId", VariableValue.StringValue("user-123"))
context = context.withVariable("user", VariableValue.ObjectValue(mapOf(
    "id" to VariableValue.StringValue("user-123"),
    "name" to VariableValue.StringValue("João"),
    "age" to VariableValue.NumberValue(25)
)))

// 6. Executar (Phase 2 - não disponível em Phase 1)
// val result = flow.execute(context)
```

### Serialização JSON

```kotlin
// Exportar fluxo para JSON
val json = kotlinx.serialization.json.Json.encodeToString(Flow.serializer(), flow)
println(json)

// Importar fluxo de JSON
val flowFromJson = kotlinx.serialization.json.Json.decodeFromString(
    Flow.serializer(),
    jsonString
)
```

## 🏛️ Princípios Arquiteturais

### 1. **Imutabilidade**
Todas as entidades de domínio são imutáveis (data classes com `val`). Uma vez criadas, não podem mudar.

```kotlin
// ❌ Impossível: propriedades são val (read-only)
flow.components = newComponents

// ✅ Correto: criar novo fluxo
val newFlow = flow.copy(components = newComponents)
```

### 2. **Tipagem Forte**
Uso máximo de tipos Kotlin para segurança em tempo de compilação.

```kotlin
// Sistema de tipos: cada porta tem tipo
val port = Port(
    id = "port-1",
    portType = PortType.OBJECT,  // Só aceita objetos
    // ...
)

// VariableValue é sealed class
when (val value = context.getVariable("key")) {
    is VariableValue.StringValue -> println("String: ${value.value}")
    is VariableValue.NumberValue -> println("Number: ${value.value}")
    // ...
    else -> println("Outro tipo")
}
```

### 3. **Independência de Plataforma**
Código puro Kotlin em `commonMain` - sem dependências de Android, iOS, JS.

```
commonMain/     ← Compilável para JVM, JS, Android, iOS
├── jvmMain/    ← Específico JVM (interfaces nativas, etc)
├── jsMain/     ← Específico JS (APIs web, etc)
├── androidMain/ ← Específico Android
└── iosMain/    ← Específico iOS
```

### 4. **Serialização Nativa**
kotlinx.serialization garante que todas as entidades sejam serializáveis.

```kotlin
@Serializable
data class Flow(
    val id: String,
    val name: String,
    // ...
)
```

### 5. **Validação Fail-Fast**
Validação ocorre no construtor - se estiver inválido, lança exceção imediatamente.

```kotlin
val flow = Flow(...) // Lança InvalidFlowException se houver problemas

// Nunca terá estado inválido
```

## 📚 Entidades de Domínio

### Flow

```kotlin
@Serializable
data class Flow(
    val id: String,
    val name: String,
    val version: String,
    val components: List<Component>,
    val connections: List<Connection>,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        // Validação automática
        validate()
    }
    
    fun validate(): Result<Unit>
    fun getComponentById(id: String): Component?
    fun getConnections(componentId: String): List<Connection>
    fun getStartComponent(): StartComponent
    fun getEndComponents(): List<EndComponent>
}
```

### Component

```kotlin
sealed class Component {
    abstract val id: String
    abstract val name: String
    abstract val properties: Map<String, ComponentProperty>
    abstract val ports: Map<String, Port>
}
```

### Port

```kotlin
@Serializable
data class Port(
    val id: String,
    val name: String,
    val direction: PortDirection,
    val portType: PortType = PortType.ANY,
    val multiple: Boolean = false
)

enum class PortDirection {
    INPUT, OUTPUT
}

enum class PortType {
    ANY, OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
}
```

## 🧪 Testes

### Cobertura

Alvo: **95%+** de cobertura em todas as plataformas

```bash
# Gerar relatório de cobertura
./gradlew test --info

# Relatório em:
# build/reports/jacoco/test/html/index.html (JVM)
```

### Exemplo de Teste

```kotlin
class FlowTest {
    
    @Test
    fun `flow deve ter exatamente um START`() {
        val flow = Flow(
            id = "test",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                ActionComponent(...),  // ❌ Sem START
                EndComponent(...)
            ),
            connections = listOf()
        )
        
        // Deve lançar InvalidFlowException
        assertThrows<InvalidFlowException> {
            flow.validate()
        }
    }
    
    @Test
    fun `flow deve ter pelo menos um END`() {
        val flow = Flow(
            id = "test",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(...),
                ActionComponent(...)
                // ❌ Sem END
            ),
            connections = listOf()
        )
        
        // Deve lançar InvalidFlowException
        assertThrows<InvalidFlowException> {
            flow.validate()
        }
    }
}
```

## 📦 Publicação em Maven Local

Para usar em outros projetos localmente:

```bash
# Publicar em Maven local (~/.m2/repository)
./gradlew publishToMavenLocal

# Então usar em outro projeto:
# build.gradle.kts
repositories {
    mavenLocal()
}

dependencies {
    implementation("io.flowmobile:flow-engine-core:1.0.0")
}
```

Ver [PUBLISHING.md](./PUBLISHING.md) para publicar em repositórios remotos.

## 🔗 Dependências

| Dependência | Versão | Escopo | Uso |
|------------|--------|--------|-----|
| **Kotlin Stdlib** | 1.9.0+ | common | Core |
| **kotlinx.serialization** | 1.6.0+ | common | JSON |
| **Kotlin Test** | 1.9.0+ | test | Unit tests |
| **JUnit** | 4.13+ | jvmTest | JVM tests |

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
}

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    androidTarget()
    iosSimulatorArm64()
    iosArm64()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }
    }
}
```

## 🔄 Integração com Outros Projetos

### Android App

```kotlin
// flow-android-app/build.gradle.kts
dependencies {
    implementation("io.flowmobile:flow-engine-core:1.0.0") { 
        // Usa target androidTarget() do flow-engine-core
    }
}

// Em Activity/ViewModel
val flow: Flow = ...
val context = ExecutionContext(flowId = flow.id, executionId = "...")
// Phase 2: val result = flow.execute(context)
```

### iOS App

```swift
// flow-ios-app/Package.swift
import FlowEngineCore

let flow: Flow = ...
var context = ExecutionContext(flowId: flow.id, executionId: "...")
// Phase 2: let result = try flow.execute(context: context)
```

### Web App

```typescript
// flow-web-app (JavaScript)
import { Flow, ExecutionContext } from 'flow-engine-core'

const flow: Flow = ...
let context = new ExecutionContext(flow.id, "...")
// Phase 2: const result = await flow.execute(context)
```

### Sandbox Service

```kotlin
// flow-sandbox-service/build.gradle.kts
dependencies {
    implementation(project(":flow-engine-core"))
}

// Usa para validação e execução de fluxos
val flow = Json.decodeFromString<Flow>(flowJson)
val result = flow.validate()
```

## 🛠️ Desenvolvimento

### Adicionar Nova Entidade

1. **Criar classe em `commonMain/kotlin/io/flowmobile/core/domain/`**

```kotlin
@Serializable
data class NewEntity(
    val id: String,
    val name: String,
    // ...
) {
    init {
        // Validações
        require(id.isNotBlank()) { "ID não pode ser vazio" }
    }
}
```

2. **Adicionar testes em `commonTest/`**

```kotlin
class NewEntityTest {
    @Test
    fun `deve criar nova entidade`() {
        val entity = NewEntity(id = "1", name = "Test")
        assertEquals("1", entity.id)
    }
}
```

3. **Integrar com Flow (se aplicável)**

```kotlin
// Flow.kt
data class Flow(
    // ...
    val newEntity: NewEntity?,
    // ...
)
```

### Workflow de Desenvolvimento

```bash
# 1. Criar branch
git checkout -b feature/nova-feature

# 2. Fazer mudanças e executar testes
./gradlew test

# 3. Commit
git add -A && git commit -m "Adiciona nova feature"

# 4. Push
git push origin feature/nova-feature

# 5. PR/Merge após review
```

## 📖 Documentação Relacionada

- [ROADMAP.md](../ROADMAP.md) - Plano completo da plataforma
- [flow-sandbox-service/README.md](../flow-sandbox-service/README.md) - Execução determinística
- [flow-android-app/README.md](../flow-android-app/README.md) - App Android
- [flow-ios-app/README.md](../flow-ios-app/README.md) - App iOS
- [flow-web-app/README.md](../flow-web-app/README.md) - Web app
- [PUBLISHING.md](./PUBLISHING.md) - Publicação do módulo

## 🧬 Arquitetura Detalhada

```
SHARED CODE (commonMain)
├── Domain Model
│   ├── Flow (estrutura, validação)
│   ├── Components (START, END, ACTION, DECISION)
│   ├── Ports & Connections
│   ├── ExecutionContext
│   └── Types (VariableValue, ComponentProperty)
│
├── Extensions
│   ├── flowUtils (topologicalSort, hasCycle, findPath)
│   └── serializationUtils
│
└── Exceptions
    ├── FlowException
    ├── InvalidFlowException
    └── ComponentNotFoundException

PLATFORM-SPECIFIC (jvmMain, jsMain, androidMain, iosMain)
├── Executors (Phase 2)
├── Platform Context
└── Host Service Adapters
```

## ⚙️ Configurações de Build

### gradle.properties

```properties
# Versões
kotlin.version=1.9.0
serialization.version=1.6.0

# Targets
android.minSdk=24
java.version=17

# Comportamento
kotlin.js.compiler=ir
kotlin.native.enableDomainObjectsSerialization=true
```

## 🤝 Contribuindo

Veja [CONTRIBUTING.md](../CONTRIBUTING.md) para diretrizes.

## 📄 Licença

Apache License 2.0 - Copyright © 2026 FlowMobile Platform
