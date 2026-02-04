package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementação mock do StorageService para testes e sandbox.
 * 
 * Capacidades:
 * - Predefined Responses: Valores pré-configurados
 * - Operation Recording: Todas as chamadas são gravadas
 * - Failure Injection: Simula falhas controladas
 * - Latency Simulation: Adiciona delay artificial
 * - Deterministic Mode: Mesmo input sempre produz mesmo output
 */
class MockStorageService(
    /** Valores iniciais pré-configurados */
    private val predefinedValues: Map<String, String> = emptyMap(),
    
    /** Se deve simular falhas */
    var shouldFail: Boolean = false,
    
    /** Mensagem de erro quando shouldFail é true */
    var failureMessage: String = "Falha simulada no StorageService",
    
    /** Latência artificial em milissegundos */
    var latencyMs: Long = 0
) : StorageService {
    
    private val storage = mutableMapOf<String, String>().apply { 
        putAll(predefinedValues) 
    }
    
    private val _recordedOperations = mutableListOf<StorageOperation>()
    
    /** Lista de operações gravadas para verificação em testes */
    val recordedOperations: List<StorageOperation> get() = _recordedOperations.toList()
    
    private val observers = mutableMapOf<String, MutableStateFlow<String?>>()
    
    private suspend fun simulateLatency() {
        if (latencyMs > 0) {
            delay(latencyMs)
        }
    }
    
    private fun checkFailure() {
        if (shouldFail) {
            throw MockServiceException(failureMessage)
        }
    }
    
    private fun recordOperation(operation: StorageOperation) {
        _recordedOperations.add(operation)
    }
    
    override suspend fun get(key: String): String? {
        simulateLatency()
        checkFailure()
        
        val value = storage[key]
        recordOperation(StorageOperation.Get(key, value))
        return value
    }
    
    override suspend fun set(key: String, value: String) {
        simulateLatency()
        checkFailure()
        
        storage[key] = value
        recordOperation(StorageOperation.Set(key, value))
        
        // Notifica observers
        observers[key]?.value = value
    }
    
    override suspend fun remove(key: String): Boolean {
        simulateLatency()
        checkFailure()
        
        val existed = storage.containsKey(key)
        storage.remove(key)
        recordOperation(StorageOperation.Remove(key, existed))
        
        // Notifica observers
        observers[key]?.value = null
        
        return existed
    }
    
    override suspend fun clear() {
        simulateLatency()
        checkFailure()
        
        val keyCount = storage.size
        storage.clear()
        recordOperation(StorageOperation.Clear(keyCount))
        
        // Notifica todos os observers
        observers.values.forEach { it.value = null }
    }
    
    override suspend fun keys(): Set<String> {
        simulateLatency()
        checkFailure()
        
        val allKeys = storage.keys.toSet()
        recordOperation(StorageOperation.Keys(allKeys))
        return allKeys
    }
    
    override suspend fun contains(key: String): Boolean {
        simulateLatency()
        checkFailure()
        
        val exists = storage.containsKey(key)
        recordOperation(StorageOperation.Contains(key, exists))
        return exists
    }
    
    override fun observe(key: String): Flow<String?> {
        val flow = observers.getOrPut(key) { 
            MutableStateFlow(storage[key]) 
        }
        recordOperation(StorageOperation.Observe(key))
        return flow.asStateFlow()
    }
    
    override suspend fun getMultiple(keys: Set<String>): Map<String, String> {
        simulateLatency()
        checkFailure()
        
        val result = keys.mapNotNull { key ->
            storage[key]?.let { key to it }
        }.toMap()
        recordOperation(StorageOperation.GetMultiple(keys, result))
        return result
    }
    
    override suspend fun setMultiple(values: Map<String, String>) {
        simulateLatency()
        checkFailure()
        
        storage.putAll(values)
        recordOperation(StorageOperation.SetMultiple(values))
        
        // Notifica observers
        values.forEach { (key, value) ->
            observers[key]?.value = value
        }
    }
    
    // Métodos auxiliares para testes
    
    /**
     * Limpa o registro de operações.
     */
    fun clearRecordedOperations() {
        _recordedOperations.clear()
    }
    
    /**
     * Verifica se uma operação específica foi executada.
     */
    fun hasOperation(predicate: (StorageOperation) -> Boolean): Boolean {
        return recordedOperations.any(predicate)
    }
    
    /**
     * Conta quantas vezes um tipo de operação foi executada.
     */
    fun countOperationsByType(type: kotlin.reflect.KClass<out StorageOperation>): Int {
        return recordedOperations.count { type.isInstance(it) }
    }
    
    /**
     * Retorna o estado atual do storage (para inspeção em testes).
     */
    fun getCurrentState(): Map<String, String> = storage.toMap()
    
    /**
     * Reseta o mock para o estado inicial.
     */
    fun reset() {
        storage.clear()
        storage.putAll(predefinedValues)
        _recordedOperations.clear()
        shouldFail = false
        latencyMs = 0
    }
}

/**
 * Representa uma operação de storage gravada.
 */
sealed class StorageOperation {
    abstract val timestamp: Long
    
    data class Get(
        val key: String,
        val returnedValue: String?,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class Set(
        val key: String,
        val value: String,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class Remove(
        val key: String,
        val existed: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class Clear(
        val keyCount: Int,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class Keys(
        val keys: kotlin.collections.Set<String>,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class Contains(
        val key: String,
        val result: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class Observe(
        val key: String,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class GetMultiple(
        val keys: kotlin.collections.Set<String>,
        val result: Map<String, String>,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
    
    data class SetMultiple(
        val values: Map<String, String>,
        override val timestamp: Long = currentTimeMillis()
    ) : StorageOperation()
}

/**
 * Retorna timestamp atual em milissegundos.
 */
internal fun currentTimeMillis(): Long = 
    kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
