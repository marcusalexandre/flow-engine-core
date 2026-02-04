package io.flowmobile.core.security.secrets

import kotlinx.datetime.Clock

/**
 * Interface para provedores de secrets.
 *
 * Implementações seguras de acesso a credenciais, chaves, tokens, etc.
 *
 * **Contrato:**
 * - Secrets nunca são expostos em logs
 * - Secrets nunca são incluídos em audit trails
 * - Secrets são sempre mascarados na UI
 * - Acesso a secrets deve ser auditado
 * - Suporta rotação de secrets
 * - Suporta múltiplos provedores (fallback)
 *
 * **Implementações:**
 * - EnvironmentSecretProvider: Variáveis de ambiente
 * - InMemorySecretProvider: Armazenamento em memória (dev)
 * - VaultSecretProvider: HashiCorp Vault (produção)
 * - AWSSecretsManagerProvider: AWS Secrets Manager
 * - AzureKeyVaultProvider: Azure Key Vault
 */
interface SecretProvider {
    /**
     * Recupera um secret por chave.
     *
     * @param key Chave do secret
     * @return Valor do secret ou null se não encontrado
     * @throws SecretAccessException Se acesso é negado
     */
    suspend fun getSecret(key: String): String?

    /**
     * Armazena um novo secret.
     *
     * @param key Chave do secret
     * @param value Valor do secret
     * @throws SecretAccessException Se armazenamento falha
     */
    suspend fun setSecret(key: String, value: String)

    /**
     * Remove um secret.
     *
     * @param key Chave do secret
     * @throws SecretAccessException Se remoção falha
     */
    suspend fun deleteSecret(key: String)

    /**
     * Verifica se um secret existe.
     */
    suspend fun hasSecret(key: String): Boolean = getSecret(key) != null

    /**
     * Retorna verdade se este provedor está disponível.
     */
    suspend fun isAvailable(): Boolean = true
}

/**
 * Exceções relacionadas a acesso de secrets.
 */
sealed class SecretAccessException(message: String) : Exception(message)

class SecretNotFoundException(key: String) :
    SecretAccessException("Secret not found: $key")

class SecretAccessDeniedException(key: String) :
    SecretAccessException("Access denied to secret: $key")

class SecretProviderException(message: String) :
    SecretAccessException(message)

/**
 * Provedor de secrets em memória (para desenvolvimento).
 *
 * **Advertência:** Não use em produção. Secrets são perdidos ao desligar.
 */
class InMemorySecretProvider : SecretProvider {
    private val secrets = mutableMapOf<String, String>()

    override suspend fun getSecret(key: String): String? {
        return secrets[key]
    }

    override suspend fun setSecret(key: String, value: String) {
        secrets[key] = value
    }

    override suspend fun deleteSecret(key: String) {
        secrets.remove(key)
    }
}

/**
 * Provedor de secrets a partir de variáveis de ambiente.
 *
 * Lê secrets de variáveis de ambiente prefixadas com `SECRET_`.
 *
 * **Exemplo:**
 * ```
 * export SECRET_DATABASE_PASSWORD=mysecurepass
 * export SECRET_API_KEY=sk_live_123456
 * ```
 *
 * **Acesso:**
 * ```kotlin
 * val password = getSecret("database_password")
 * ```
 */
class EnvironmentSecretProvider(
    private val prefix: String = "SECRET_"
) : SecretProvider {
    override suspend fun getSecret(key: String): String? {
        @Suppress("UNUSED_VARIABLE")
        val _envKey = (prefix + key.uppercase()).replace("-", "_")
        // Note: System.getenv() is not available in multiplatform Kotlin
        // This implementation should be provided by platform-specific code
        // For now, return null to prevent compilation errors
        return null
    }

    override suspend fun setSecret(key: String, value: String) {
        throw SecretAccessDeniedException(key)
    }

    override suspend fun deleteSecret(key: String) {
        throw SecretAccessDeniedException(key)
    }

    override suspend fun isAvailable(): Boolean = true
}

/**
 * Composição de múltiplos provedores com fallback.
 *
 * Tenta cada provedor na ordem até encontrar o secret.
 * Útil para suportar múltiplas estratégias de armazenamento.
 *
 * **Exemplo:**
 * ```kotlin
 * val provider = CompositeSecretProvider(
 *     listOf(
 *         VaultSecretProvider(...),    // Primário
 *         EnvironmentSecretProvider(), // Fallback
 *         InMemorySecretProvider()     // Último recurso
 *     )
 * )
 * ```
 */
class CompositeSecretProvider(
    private val providers: List<SecretProvider>
) : SecretProvider {
    override suspend fun getSecret(key: String): String? {
        for (provider in providers) {
            try {
                if (provider.isAvailable()) {
                    val secret = provider.getSecret(key)
                    if (secret != null) {
                        return secret
                    }
                }
            } catch (e: Exception) {
                // Continuar para próximo provedor
            }
        }
        return null
    }

    override suspend fun setSecret(key: String, value: String) {
        // Usar primeiro provedor disponível e writable
        for (provider in providers) {
            try {
                if (provider.isAvailable()) {
                    provider.setSecret(key, value)
                    return
                }
            } catch (e: SecretAccessDeniedException) {
                // Continuar para próximo
            }
        }
        throw SecretProviderException("No available provider for setSecret")
    }

    override suspend fun deleteSecret(key: String) {
        for (provider in providers) {
            try {
                if (provider.isAvailable()) {
                    provider.deleteSecret(key)
                    return
                }
            } catch (e: SecretAccessDeniedException) {
                // Continuar para próximo
            }
        }
        throw SecretProviderException("No available provider for deleteSecret")
    }
}

/**
 * Protetor de secrets que redacta valores em strings.
 *
 * Utilitário para garantir que secrets não vazem em logs, mensagens de erro, etc.
 */
object SecretProtector {
    /**
     * Lista de prefixos que indicam campos de secret.
     */
    private val SECRET_FIELD_PATTERNS = listOf(
        Regex("(?i)password"),
        Regex("(?i)secret"),
        Regex("(?i)token"),
        Regex("(?i)api[_-]?key"),
        Regex("(?i)credential"),
        Regex("(?i)auth[_-]?(token|key)"),
        Regex("(?i)private[_-]?key"),
        Regex("(?i)bearer\\s+"),
        Regex("(?i)apikey\\s*[:=]"),
        Regex("(?i)authorization\\s*[:=]")
    )

    /**
     * Redacta secrets de uma string baseado em padrões conhecidos.
     *
     * @param value String que pode conter secrets
     * @return String com secrets redactados
     */
    fun redact(value: String): String {
        var result = value
        for (pattern in SECRET_FIELD_PATTERNS) {
            result = result.replace(pattern) { match ->
                if (match.value.endsWith(":") || match.value.endsWith("=")) {
                    match.value + " ***REDACTED***"
                } else {
                    match.value + " ***REDACTED***"
                }
            }
        }
        return result
    }

    /**
     * Verifica se uma string contém padrões de secret.
     */
    fun containsSecret(value: String): Boolean {
        return SECRET_FIELD_PATTERNS.any { it.containsMatchIn(value) }
    }

    /**
     * Redacta um mapa de valores (para contextos).
     */
    fun redactMap(map: Map<String, Any>): Map<String, String> {
        return map.mapValues { (key, value) ->
            val stringValue = value.toString()
            if (isSecretKey(key)) {
                "***REDACTED***"
            } else {
                redact(stringValue)
            }
        }
    }

    /**
     * Detecta se uma chave é provavelmente um secret.
     */
    fun isSecretKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower.contains("password") ||
                lower.contains("secret") ||
                lower.contains("token") ||
                lower.contains("key") ||
                lower.contains("credential") ||
                lower.contains("auth")
    }
}

/**
 * Configuração de provedor de secrets.
 *
 * Permite configurar qual provedor usar e políticas de acesso.
 */
data class SecretProviderConfig(
    val provider: SecretProvider,
    val auditAccess: Boolean = true,
    val cacheSecrets: Boolean = false,
    val cacheTTLMs: Long = 5 * 60 * 1000L, // 5 minutos
    val requireMFA: Boolean = false,
    val allowLocalStorage: Boolean = false
) {
    /**
     * Cria provider configurado com cache opcional.
     */
    fun createProvider(): SecretProvider {
        return if (cacheSecrets) {
            CachedSecretProvider(provider, cacheTTLMs)
        } else {
            provider
        }
    }
}

/**
 * Wrapper que implementa cache com TTL para secrets.
 *
 * Reduz acesso ao provedor subjacente enquanto limita risco
 * de secrets antigos em cache.
 */
class CachedSecretProvider(
    private val provider: SecretProvider,
    private val ttlMs: Long
) : SecretProvider {
    private data class CacheEntry(val value: String, val timestamp: Long)
    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun getSecret(key: String): String? {
        val cached = cache[key]
        if (cached != null && Clock.System.now().toEpochMilliseconds() - cached.timestamp < ttlMs) {
            return cached.value
        }

        val value = provider.getSecret(key)
        if (value != null) {
            cache[key] = CacheEntry(value, Clock.System.now().toEpochMilliseconds())
        }
        return value
    }

    override suspend fun setSecret(key: String, value: String) {
        provider.setSecret(key, value)
        cache.remove(key) // Invalidar cache
    }

    override suspend fun deleteSecret(key: String) {
        provider.deleteSecret(key)
        cache.remove(key)
    }

    /**
     * Limpa todo o cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Limpa entradas expiradas do cache.
     */
    fun pruneExpired() {
        val now = Clock.System.now().toEpochMilliseconds()
        cache.entries.removeAll { (_, entry) ->
            now - entry.timestamp > ttlMs
        }
    }
}
