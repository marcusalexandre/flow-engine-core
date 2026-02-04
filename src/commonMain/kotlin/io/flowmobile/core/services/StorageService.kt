package io.flowmobile.core.services

import io.flowmobile.core.domain.VariableValue
import kotlinx.coroutines.flow.Flow

/**
 * Serviço de armazenamento persistente local.
 * 
 * Implementações específicas por plataforma:
 * - Android: SharedPreferences, DataStore, EncryptedSharedPreferences
 * - iOS: UserDefaults, Keychain
 * - Web: LocalStorage, IndexedDB
 */
interface StorageService {
    
    /**
     * Obtém um valor do armazenamento.
     *
     * @param key Chave do valor a obter
     * @return O valor armazenado ou null se não existir
     */
    suspend fun get(key: String): String?
    
    /**
     * Armazena um valor.
     *
     * @param key Chave para identificar o valor
     * @param value Valor a armazenar
     */
    suspend fun set(key: String, value: String)
    
    /**
     * Remove um valor do armazenamento.
     *
     * @param key Chave do valor a remover
     * @return true se o valor existia e foi removido
     */
    suspend fun remove(key: String): Boolean
    
    /**
     * Limpa todos os valores do armazenamento.
     */
    suspend fun clear()
    
    /**
     * Retorna todas as chaves armazenadas.
     *
     * @return Conjunto de todas as chaves
     */
    suspend fun keys(): Set<String>
    
    /**
     * Verifica se uma chave existe no armazenamento.
     *
     * @param key Chave a verificar
     * @return true se a chave existe
     */
    suspend fun contains(key: String): Boolean
    
    /**
     * Observa mudanças em uma chave específica.
     *
     * @param key Chave a observar
     * @return Flow que emite novos valores quando a chave é atualizada
     */
    fun observe(key: String): Flow<String?>
    
    /**
     * Obtém múltiplos valores de uma vez.
     *
     * @param keys Lista de chaves a obter
     * @return Mapa de chave para valor (chaves não encontradas não são incluídas)
     */
    suspend fun getMultiple(keys: Set<String>): Map<String, String>
    
    /**
     * Armazena múltiplos valores de uma vez (operação atômica).
     *
     * @param values Mapa de chave para valor
     */
    suspend fun setMultiple(values: Map<String, String>)
}

/**
 * Resultado de uma operação de storage.
 */
sealed class StorageResult {
    /**
     * Operação bem-sucedida.
     *
     * @param value Valor retornado (pode ser null para operações que não retornam valor)
     */
    data class Success(val value: String? = null) : StorageResult()
    
    /**
     * Operação falhou.
     *
     * @param error Descrição do erro
     * @param exception Exceção original (se disponível)
     */
    data class Failure(val error: String, val exception: Throwable? = null) : StorageResult()
}
