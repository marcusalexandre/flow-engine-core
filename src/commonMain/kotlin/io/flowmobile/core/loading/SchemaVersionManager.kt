package io.flowmobile.core.loading

/**
 * Gerenciador de versões de schema JSON para fluxos.
 *
 * O SchemaVersionManager é responsável por:
 * - Verificar se uma versão de schema é suportada
 * - Fornecer migrações entre versões
 * - Gerar avisos de deprecação para versões antigas
 * - Manter compatibilidade retroativa
 *
 * Política de Versionamento:
 * - Major: Breaking changes que requerem migração
 * - Minor: Novas features backward-compatible
 * - Patch: Bug fixes no schema
 *
 * Suporte a Versões:
 * - Versão atual: sempre suportada
 * - N-1 major: suportada com deprecation warnings
 * - N-2 major: suportada apenas para leitura (read-only)
 * - N-3 major: não suportada, erro de carregamento
 */
class SchemaVersionManager {
    
    companion object {
        /**
         * Versão atual do schema.
         */
        const val CURRENT_VERSION = "1.0.0"
        
        /**
         * Versões suportadas organizadas por major version.
         */
        private val SUPPORTED_VERSIONS = mapOf(
            1 to listOf("1.0.0", "1.0.1", "1.1.0"),
            0 to listOf("0.9.0", "0.9.1") // Legacy, apenas leitura
        )
        
        /**
         * Versões deprecadas (N-1 major ou anterior).
         */
        private val DEPRECATED_VERSIONS = setOf("0.9.0", "0.9.1")
        
        /**
         * Versões read-only (N-2 major).
         */
        private val READ_ONLY_VERSIONS = setOf("0.9.0", "0.9.1")
    }
    
    /**
     * Retorna a versão atual do schema.
     */
    fun getCurrentVersion(): String = CURRENT_VERSION
    
    /**
     * Verifica se uma versão é suportada.
     *
     * @param version Versão a verificar
     * @return true se a versão é suportada
     */
    fun isSupported(version: String): Boolean {
        return getAllSupportedVersions().contains(version)
    }
    
    /**
     * Verifica se uma versão está deprecada.
     *
     * @param version Versão a verificar
     * @return true se a versão está deprecada
     */
    fun isDeprecated(version: String): Boolean {
        return DEPRECATED_VERSIONS.contains(version)
    }
    
    /**
     * Verifica se uma versão é apenas para leitura.
     *
     * @param version Versão a verificar
     * @return true se a versão é read-only
     */
    fun isReadOnly(version: String): Boolean {
        return READ_ONLY_VERSIONS.contains(version)
    }
    
    /**
     * Retorna todas as versões suportadas.
     */
    fun getSupportedVersions(): List<String> {
        return getAllSupportedVersions()
    }
    
    /**
     * Retorna um migrador entre duas versões, se disponível.
     *
     * @param from Versão de origem
     * @param to Versão de destino
     * @return SchemaMigrator ou null se não houver caminho de migração
     */
    fun getMigrator(from: String, to: String): SchemaMigrator? {
        // Verificar se ambas versões são válidas
        if (!isSupported(from) || !isSupported(to)) {
            return null
        }
        
        // Parsear versões
        val fromParsed = parseVersion(from) ?: return null
        val toParsed = parseVersion(to) ?: return null
        
        // Verificar se migração é possível (só para frente)
        if (compareVersions(fromParsed, toParsed) >= 0) {
            return null // Não pode migrar para versão anterior ou igual
        }
        
        // Construir cadeia de migrações
        return ChainedMigrator(from, to, buildMigrationChain(from, to))
    }
    
    /**
     * Migra um JSON de uma versão para outra.
     *
     * @param json JSON no formato da versão de origem
     * @param targetVersion Versão de destino
     * @return MigrationResult com o JSON migrado ou erros
     */
    fun migrate(json: String, targetVersion: String): MigrationResult {
        val parser = FlowParser()
        val parseResult = parser.parse(json)
        
        if (parseResult.isFailure()) {
            return MigrationResult(
                success = false,
                migratedJson = null,
                errors = parseResult.errorsOrEmpty().map { it.message }
            )
        }
        
        val document = parseResult.getOrThrow()
        val currentVersion = document.schemaVersion
        
        if (currentVersion == targetVersion) {
            return MigrationResult(
                success = true,
                migratedJson = json,
                warnings = listOf("JSON já está na versão alvo")
            )
        }
        
        val migrator = getMigrator(currentVersion, targetVersion)
        if (migrator == null) {
            return MigrationResult(
                success = false,
                migratedJson = null,
                errors = listOf("Não há caminho de migração de $currentVersion para $targetVersion")
            )
        }
        
        return migrator.migrate(json)
    }
    
    /**
     * Retorna avisos de deprecação para uma versão.
     *
     * @param version Versão a verificar
     * @return Lista de avisos de deprecação
     */
    fun getDeprecationWarnings(version: String): List<LoadWarning> {
        val warnings = mutableListOf<LoadWarning>()
        
        if (isDeprecated(version)) {
            warnings.add(LoadWarning(
                code = "DEPRECATED_SCHEMA_VERSION",
                message = "Schema versão '$version' está deprecado. " +
                        "Considere migrar para a versão $CURRENT_VERSION",
                path = "schemaVersion"
            ))
        }
        
        if (isReadOnly(version)) {
            warnings.add(LoadWarning(
                code = "READ_ONLY_SCHEMA_VERSION",
                message = "Schema versão '$version' é suportado apenas para leitura. " +
                        "Novos fluxos devem usar a versão $CURRENT_VERSION",
                path = "schemaVersion"
            ))
        }
        
        // Avisos específicos por versão
        when (version) {
            "0.9.0", "0.9.1" -> {
                warnings.add(LoadWarning(
                    code = "LEGACY_SCHEMA",
                    message = "Esta versão de schema será removida na próxima major release. " +
                            "Use o migrador para atualizar para $CURRENT_VERSION",
                    path = "schemaVersion"
                ))
            }
        }
        
        return warnings
    }
    
    /**
     * Retorna a lista de todas as versões suportadas.
     */
    private fun getAllSupportedVersions(): List<String> {
        return SUPPORTED_VERSIONS.values.flatten()
    }
    
    /**
     * Parseia uma string de versão semântica.
     */
    private fun parseVersion(version: String): SemanticVersion? {
        val parts = version.split(".")
        if (parts.size != 3) return null
        
        return try {
            SemanticVersion(
                major = parts[0].toInt(),
                minor = parts[1].toInt(),
                patch = parts[2].toInt()
            )
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * Compara duas versões.
     * @return Negativo se v1 < v2, 0 se iguais, positivo se v1 > v2
     */
    private fun compareVersions(v1: SemanticVersion, v2: SemanticVersion): Int {
        if (v1.major != v2.major) return v1.major - v2.major
        if (v1.minor != v2.minor) return v1.minor - v2.minor
        return v1.patch - v2.patch
    }
    
    /**
     * Constrói a cadeia de migrações entre duas versões.
     */
    private fun buildMigrationChain(from: String, to: String): List<SingleMigration> {
        val migrations = mutableListOf<SingleMigration>()
        
        // Para este exemplo, implementamos migrações específicas
        when {
            from.startsWith("0.9") && to.startsWith("1.") -> {
                // Migração 0.9.x -> 1.0.0
                migrations.add(Migration_0_9_to_1_0())
            }
            from == "1.0.0" && to == "1.0.1" -> {
                // Migração 1.0.0 -> 1.0.1 (apenas patch, sem mudanças estruturais)
                migrations.add(NoOpMigration("1.0.0", "1.0.1"))
            }
            from == "1.0.1" && to == "1.1.0" -> {
                // Migração 1.0.1 -> 1.1.0
                migrations.add(NoOpMigration("1.0.1", "1.1.0"))
            }
        }
        
        return migrations
    }
}

/**
 * Versão semântica parseada.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    override fun toString(): String = "$major.$minor.$patch"
}

/**
 * Interface para migradores de schema.
 */
interface SchemaMigrator {
    val fromVersion: String
    val toVersion: String
    fun migrate(json: String): MigrationResult
}

/**
 * Migração única entre duas versões adjacentes.
 */
interface SingleMigration {
    val fromVersion: String
    val toVersion: String
    fun transform(json: String): String
}

/**
 * Migrador que encadeia múltiplas migrações.
 */
class ChainedMigrator(
    override val fromVersion: String,
    override val toVersion: String,
    private val migrations: List<SingleMigration>
) : SchemaMigrator {
    
    override fun migrate(json: String): MigrationResult {
        var currentJson = json
        val warnings = mutableListOf<String>()
        
        try {
            for (migration in migrations) {
                currentJson = migration.transform(currentJson)
                warnings.add("Migrado de ${migration.fromVersion} para ${migration.toVersion}")
            }
            
            return MigrationResult(
                success = true,
                migratedJson = currentJson,
                warnings = warnings
            )
        } catch (e: Exception) {
            return MigrationResult(
                success = false,
                migratedJson = null,
                errors = listOf("Erro durante migração: ${e.message}")
            )
        }
    }
}

/**
 * Migração que não faz alterações (para patches sem mudanças estruturais).
 */
class NoOpMigration(
    override val fromVersion: String,
    override val toVersion: String
) : SingleMigration {
    
    override fun transform(json: String): String {
        // Apenas atualiza a versão no JSON
        return json.replace(
            "\"schemaVersion\": \"$fromVersion\"",
            "\"schemaVersion\": \"$toVersion\""
        ).replace(
            "\"schemaVersion\":\"$fromVersion\"",
            "\"schemaVersion\":\"$toVersion\""
        )
    }
}

/**
 * Migração de 0.9.x para 1.0.0.
 * Trata mudanças estruturais como:
 * - Renomeação de campos
 * - Adição de campos obrigatórios
 * - Mudança de formato
 */
class Migration_0_9_to_1_0 : SingleMigration {
    
    override val fromVersion: String = "0.9.x"
    override val toVersion: String = "1.0.0"
    
    override fun transform(json: String): String {
        var result = json
        
        // 1. Atualizar versão do schema
        result = result.replace(
            Regex("\"schemaVersion\"\\s*:\\s*\"0\\.9\\.[0-9]+\""),
            "\"schemaVersion\": \"1.0.0\""
        )
        
        // 2. Renomear campos deprecados (exemplo)
        // "nodes" -> "components"
        result = result.replace("\"nodes\"", "\"components\"")
        
        // "edges" -> "connections"
        result = result.replace("\"edges\"", "\"connections\"")
        
        // 3. Converter formato de referência de porta
        // "from" -> "source", "to" -> "target"
        result = result.replace("\"from\":", "\"source\":")
        result = result.replace("\"to\":", "\"target\":")
        
        return result
    }
}

/**
 * Resultado de uma migração.
 */
data class MigrationResult(
    val success: Boolean,
    val migratedJson: String?,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)
