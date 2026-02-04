package io.flowmobile.core.security.sandbox

/**
 * Política de segurança para isolamento de sandbox.
 *
 * Define regras de isolamento para diferentes recursos:
 * - Network: Sem conexões externas
 * - Filesystem: Read-only ou sem acesso
 * - Process: Limites de CPU e memória
 * - Memory: Limites por execução
 * - Time: Timeouts estritos
 */
enum class IsolationLevel {
    /** Isolamento permissivo (desenvolvimento) */
    PERMISSIVE,
    /** Isolamento padrão (produção normal) */
    STANDARD,
    /** Isolamento rigoroso (dados sensíveis) */
    STRICT
}

/**
 * Política de isolamento de rede.
 */
enum class NetworkIsolationPolicy {
    /** Permitir todas as conexões */
    ALLOW_ALL,
    /** Bloquear conexões externas, permitir localhost */
    LOCALHOST_ONLY,
    /** Bloquear todas as conexões de rede */
    BLOCK_ALL
}

/**
 * Política de isolamento de sistema de arquivos.
 */
enum class FilesystemIsolationPolicy {
    /** Permitir leitura e escrita */
    READ_WRITE,
    /** Permitir apenas leitura */
    READ_ONLY,
    /** Bloquear acesso ao filesystem */
    BLOCK_ALL
}

/**
 * Política de isolamento de processos.
 */
data class ProcessIsolationPolicy(
    val maxCpuTimeMs: Long = 30_000L,
    val maxMemoryMb: Long = 512L,
    val maxFileDescriptors: Int = 100,
    val maxProcesses: Int = 1,
    val useSeccomp: Boolean = true,
    val useAppArmor: Boolean = false
)

/**
 * Política de isolamento de tempo.
 */
data class TimeIsolationPolicy(
    val executionTimeoutMs: Long = 30_000L,
    val stepTimeoutMs: Long = 1000L,
    val wallclockTimeoutMs: Long = 60_000L,
    val allowClockAdjustment: Boolean = false
)

/**
 * Configuração completa de segurança para sandbox.
 *
 * **Níveis de Isolamento:**
 * - PERMISSIVE: Desenvolvimento local
 * - STANDARD: Produção normal
 * - STRICT: Dados sensíveis, compliance LGPD/GDPR
 *
 * **Exemplo - Modo Padrão:**
 * ```
 * Network: LOCALHOST_ONLY
 * Filesystem: READ_ONLY
 * CPU Time: 30s
 * Memory: 512MB
 * Execution Timeout: 30s
 * ```
 */
data class SandboxSecurityPolicy(
    val isolationLevel: IsolationLevel = IsolationLevel.STANDARD,
    val networkPolicy: NetworkIsolationPolicy = NetworkIsolationPolicy.LOCALHOST_ONLY,
    val filesystemPolicy: FilesystemIsolationPolicy = FilesystemIsolationPolicy.READ_ONLY,
    val processPolicy: ProcessIsolationPolicy = ProcessIsolationPolicy(),
    val timePolicy: TimeIsolationPolicy = TimeIsolationPolicy(),
    val enableMemorySanitizer: Boolean = true,
    val enableDataEncryption: Boolean = true,
    val enableSecureRandom: Boolean = true,
    val blockCryptography: Boolean = false,
    val blockShellExecution: Boolean = true,
    val blockCodeGeneration: Boolean = true,
    val blockReflection: Boolean = false,
    val blockDeserialization: Boolean = true,
    val blockJNI: Boolean = true,
    val blockNativeLibraries: Boolean = true
) {
    /**
     * Cria política padrão para ambiente específico.
     */
    companion object {
        /**
         * Política para desenvolvimento local.
         */
        fun development() = SandboxSecurityPolicy(
            isolationLevel = IsolationLevel.PERMISSIVE,
            networkPolicy = NetworkIsolationPolicy.LOCALHOST_ONLY,
            filesystemPolicy = FilesystemIsolationPolicy.READ_WRITE,
            processPolicy = ProcessIsolationPolicy(
                maxCpuTimeMs = 5 * 60_000L,
                maxMemoryMb = 2_048L,
                useSeccomp = false
            ),
            timePolicy = TimeIsolationPolicy(
                executionTimeoutMs = 5 * 60_000L,
                stepTimeoutMs = 5_000L
            ),
            blockReflection = false,
            blockCodeGeneration = false
        )

        /**
         * Política para produção padrão.
         */
        fun production() = SandboxSecurityPolicy(
            isolationLevel = IsolationLevel.STANDARD,
            networkPolicy = NetworkIsolationPolicy.LOCALHOST_ONLY,
            filesystemPolicy = FilesystemIsolationPolicy.READ_ONLY,
            processPolicy = ProcessIsolationPolicy(
                maxCpuTimeMs = 30_000L,
                maxMemoryMb = 512L,
                useSeccomp = true
            ),
            timePolicy = TimeIsolationPolicy(
                executionTimeoutMs = 30_000L,
                stepTimeoutMs = 1_000L
            ),
            blockReflection = true,
            blockCodeGeneration = true
        )

        /**
         * Política para dados sensíveis (LGPD/GDPR).
         */
        fun sensitive() = SandboxSecurityPolicy(
            isolationLevel = IsolationLevel.STRICT,
            networkPolicy = NetworkIsolationPolicy.BLOCK_ALL,
            filesystemPolicy = FilesystemIsolationPolicy.BLOCK_ALL,
            processPolicy = ProcessIsolationPolicy(
                maxCpuTimeMs = 10_000L,
                maxMemoryMb = 256L,
                useSeccomp = true,
                useAppArmor = true
            ),
            timePolicy = TimeIsolationPolicy(
                executionTimeoutMs = 10_000L,
                stepTimeoutMs = 500L
            ),
            enableMemorySanitizer = true,
            enableDataEncryption = true,
            blockReflection = true,
            blockCodeGeneration = true,
            blockCryptography = false,
            blockShellExecution = true,
            blockJNI = true,
            blockNativeLibraries = true
        )
    }

    /**
     * Valida a política para consistência.
     * @return Lista de erros (vazio se válido)
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        // Validar policies de processo
        if (processPolicy.maxCpuTimeMs <= 0) {
            errors.add("maxCpuTimeMs must be positive")
        }
        if (processPolicy.maxMemoryMb <= 0) {
            errors.add("maxMemoryMb must be positive")
        }
        if (processPolicy.maxFileDescriptors <= 0) {
            errors.add("maxFileDescriptors must be positive")
        }
        if (processPolicy.maxProcesses <= 0) {
            errors.add("maxProcesses must be positive")
        }

        // Validar policies de tempo
        if (timePolicy.executionTimeoutMs <= 0) {
            errors.add("executionTimeoutMs must be positive")
        }
        if (timePolicy.stepTimeoutMs <= 0) {
            errors.add("stepTimeoutMs must be positive")
        }
        if (timePolicy.wallclockTimeoutMs <= 0) {
            errors.add("wallclockTimeoutMs must be positive")
        }

        // Validar consistência entre timeouts
        if (timePolicy.stepTimeoutMs > timePolicy.executionTimeoutMs) {
            errors.add(
                "stepTimeoutMs (${timePolicy.stepTimeoutMs}) should not exceed " +
                "executionTimeoutMs (${timePolicy.executionTimeoutMs})"
            )
        }

        // Validar flags contraditórias
        if (blockCryptography && !enableSecureRandom) {
            errors.add("Cannot block cryptography while requiring secure random")
        }

        return errors
    }

    /**
     * Retorna descrição legível da política.
     */
    override fun toString(): String {
        return """
            SandboxSecurityPolicy(
              isolationLevel=$isolationLevel,
              networkPolicy=$networkPolicy,
              filesystemPolicy=$filesystemPolicy,
              maxCpuTime=${processPolicy.maxCpuTimeMs}ms,
              maxMemory=${processPolicy.maxMemoryMb}MB,
              executionTimeout=${timePolicy.executionTimeoutMs}ms,
              blockCodeGeneration=$blockCodeGeneration,
              blockReflection=$blockReflection,
              blockJNI=$blockJNI
            )
        """.trimIndent()
    }
}

/**
 * Enforce da política de sandbox.
 *
 * Implementa aplicação de regras de isolamento em tempo de execução.
 *
 * **Responsabilidades:**
 * - Validar que execução respeita política
 * - Bloquear operações não permitidas
 * - Registrar tentativas de violação
 * - Fornecer informações de auditoria
 */
class SandboxSecurityEnforcer(
    private val policy: SandboxSecurityPolicy
) {
    init {
        val errors = policy.validate()
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Invalid SandboxSecurityPolicy: ${errors.joinToString(", ")}")
        }
    }

    /**
     * Valida operação de rede contra política.
     *
     * @param host Host de destino
     * @param port Porta de destino
     * @throws IllegalArgumentException se operação é bloqueada
     */
    fun validateNetworkOperation(host: String, port: Int) {
        when (policy.networkPolicy) {
            NetworkIsolationPolicy.BLOCK_ALL -> {
                throw IllegalArgumentException("Network operations are blocked in this sandbox")
            }
            NetworkIsolationPolicy.LOCALHOST_ONLY -> {
                if (host != "localhost" && host != "127.0.0.1" && host != "::1") {
                    throw IllegalArgumentException(
                        "Network operations are restricted to localhost, " +
                        "attempted connection to: $host:$port"
                    )
                }
            }
            NetworkIsolationPolicy.ALLOW_ALL -> {
                // Permitir
            }
        }
    }

    /**
     * Valida operação de filesystem contra política.
     *
     * @param path Caminho do arquivo/diretório
     * @param operation Tipo de operação (READ, WRITE, EXECUTE)
     * @throws IllegalArgumentException se operação é bloqueada
     */
    fun validateFilesystemOperation(path: String, operation: FilesystemOperation) {
        when (policy.filesystemPolicy) {
            FilesystemIsolationPolicy.BLOCK_ALL -> {
                throw IllegalArgumentException(
                    "Filesystem operations are blocked in this sandbox, " +
                    "attempted $operation on: $path"
                )
            }
            FilesystemIsolationPolicy.READ_ONLY -> {
                if (operation == FilesystemOperation.WRITE || operation == FilesystemOperation.EXECUTE) {
                    throw IllegalArgumentException(
                        "Only read operations are allowed in this sandbox, " +
                        "attempted $operation on: $path"
                    )
                }
            }
            FilesystemIsolationPolicy.READ_WRITE -> {
                // Permitir
            }
        }
    }

    /**
     * Valida código Java/Kotlin contra política.
     *
     * @param feature Recurso a validar
     * @throws IllegalArgumentException se recurso é bloqueado
     */
    fun validateCodeFeature(feature: CodeFeature) {
        when (feature) {
            CodeFeature.CODE_GENERATION -> {
                if (policy.blockCodeGeneration) {
                    throw IllegalArgumentException("Code generation is blocked in this sandbox")
                }
            }
            CodeFeature.REFLECTION -> {
                if (policy.blockReflection) {
                    throw IllegalArgumentException("Reflection is blocked in this sandbox")
                }
            }
            CodeFeature.JNI -> {
                if (policy.blockJNI) {
                    throw IllegalArgumentException("JNI calls are blocked in this sandbox")
                }
            }
            CodeFeature.DESERIALIZATION -> {
                if (policy.blockDeserialization) {
                    throw IllegalArgumentException("Deserialization is blocked in this sandbox")
                }
            }
            CodeFeature.SHELL_EXECUTION -> {
                if (policy.blockShellExecution) {
                    throw IllegalArgumentException("Shell execution is blocked in this sandbox")
                }
            }
            CodeFeature.CRYPTOGRAPHY -> {
                if (policy.blockCryptography) {
                    throw IllegalArgumentException("Cryptography is blocked in this sandbox")
                }
            }
        }
    }

    /**
     * Retorna informações sobre a política para auditoria.
     */
    fun getPolicyInfo(): Map<String, String> {
        return mapOf(
            "isolationLevel" to policy.isolationLevel.toString(),
            "networkPolicy" to policy.networkPolicy.toString(),
            "filesystemPolicy" to policy.filesystemPolicy.toString(),
            "maxCpuTimeMs" to policy.processPolicy.maxCpuTimeMs.toString(),
            "maxMemoryMb" to policy.processPolicy.maxMemoryMb.toString(),
            "executionTimeoutMs" to policy.timePolicy.executionTimeoutMs.toString(),
            "blockCodeGeneration" to policy.blockCodeGeneration.toString(),
            "blockReflection" to policy.blockReflection.toString(),
            "blockJNI" to policy.blockJNI.toString(),
            "blockDeserialization" to policy.blockDeserialization.toString(),
            "blockShellExecution" to policy.blockShellExecution.toString()
        )
    }
}

/**
 * Tipos de operações de filesystem.
 */
enum class FilesystemOperation {
    READ, WRITE, EXECUTE, DELETE
}

/**
 * Recursos de código que podem ser bloqueados.
 */
enum class CodeFeature {
    CODE_GENERATION,
    REFLECTION,
    JNI,
    DESERIALIZATION,
    SHELL_EXECUTION,
    CRYPTOGRAPHY
}
