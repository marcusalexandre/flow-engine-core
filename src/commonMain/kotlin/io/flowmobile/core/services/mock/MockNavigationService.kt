package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*
import kotlinx.coroutines.delay

/**
 * Implementação mock do NavigationService para testes e sandbox.
 */
class MockNavigationService(
    /** Destino inicial */
    initialDestination: Destination? = null,
    
    /** Se deve simular falhas */
    var shouldFail: Boolean = false,
    
    /** Mensagem de erro quando shouldFail é true */
    var failureMessage: String = "Falha de navegação simulada",
    
    /** Latência artificial em milissegundos */
    var latencyMs: Long = 0
) : NavigationService {
    
    private val backStack = mutableListOf<Destination>()
    private val results = mutableMapOf<String, Any>()
    
    private val _recordedOperations = mutableListOf<NavigationOperation>()
    
    /** Lista de operações gravadas para verificação em testes */
    val recordedOperations: List<NavigationOperation> get() = _recordedOperations.toList()
    
    init {
        initialDestination?.let { backStack.add(it) }
    }
    
    private suspend fun simulateLatency() {
        if (latencyMs > 0) {
            delay(latencyMs)
        }
    }
    
    private fun checkFailure(): NavigationResult.Failure? {
        if (shouldFail) {
            return NavigationResult.Failure(failureMessage, NavigationFailureReason.UNKNOWN)
        }
        return null
    }
    
    private fun recordOperation(operation: NavigationOperation) {
        _recordedOperations.add(operation)
    }
    
    override suspend fun navigate(destination: Destination): NavigationResult {
        simulateLatency()
        
        recordOperation(NavigationOperation.Navigate(destination))
        
        checkFailure()?.let { return it }
        
        if (destination.options.launchSingleTop && backStack.lastOrNull()?.route == destination.route) {
            // Já está no topo, não adiciona novamente
            return NavigationResult.Success(destination)
        }
        
        backStack.add(destination)
        return NavigationResult.Success(destination)
    }
    
    override suspend fun navigateBack(): NavigationResult {
        simulateLatency()
        
        recordOperation(NavigationOperation.NavigateBack)
        
        checkFailure()?.let { return it }
        
        if (backStack.size <= 1) {
            return NavigationResult.Failure("Não há entradas na pilha", NavigationFailureReason.NO_BACK_STACK)
        }
        
        backStack.removeLast()
        val currentDestination = backStack.last()
        return NavigationResult.Success(currentDestination)
    }
    
    override suspend fun navigateBackTo(destination: Destination, inclusive: Boolean): NavigationResult {
        simulateLatency()
        
        recordOperation(NavigationOperation.NavigateBackTo(destination, inclusive))
        
        checkFailure()?.let { return it }
        
        val index = backStack.indexOfLast { it.route == destination.route }
        if (index == -1) {
            return NavigationResult.Failure(
                "Destino não encontrado na pilha: ${destination.route}",
                NavigationFailureReason.DESTINATION_NOT_FOUND
            )
        }
        
        val removeFromIndex = if (inclusive) index else index + 1
        while (backStack.size > removeFromIndex) {
            backStack.removeLast()
        }
        
        val currentDestination = backStack.lastOrNull() ?: destination
        return NavigationResult.Success(currentDestination)
    }
    
    override suspend fun getCurrentDestination(): Destination? {
        simulateLatency()
        
        val current = backStack.lastOrNull()
        recordOperation(NavigationOperation.GetCurrentDestination(current))
        return current
    }
    
    override suspend fun canNavigateBack(): Boolean {
        simulateLatency()
        
        val canNavigate = backStack.size > 1
        recordOperation(NavigationOperation.CanNavigateBack(canNavigate))
        return canNavigate
    }
    
    override suspend fun setResult(key: String, value: Any) {
        simulateLatency()
        
        results[key] = value
        recordOperation(NavigationOperation.SetResult(key, value))
    }
    
    override suspend fun getResult(key: String): Any? {
        simulateLatency()
        
        val value = results[key]
        recordOperation(NavigationOperation.GetResult(key, value))
        return value
    }
    
    override suspend fun navigateAndClearStack(destination: Destination): NavigationResult {
        simulateLatency()
        
        recordOperation(NavigationOperation.NavigateAndClearStack(destination))
        
        checkFailure()?.let { return it }
        
        backStack.clear()
        backStack.add(destination)
        return NavigationResult.Success(destination)
    }
    
    override suspend fun replace(destination: Destination): NavigationResult {
        simulateLatency()
        
        recordOperation(NavigationOperation.Replace(destination))
        
        checkFailure()?.let { return it }
        
        if (backStack.isNotEmpty()) {
            backStack.removeLast()
        }
        backStack.add(destination)
        return NavigationResult.Success(destination)
    }
    
    // Métodos auxiliares para testes
    
    /**
     * Retorna a pilha de navegação atual.
     */
    fun getBackStack(): List<Destination> = backStack.toList()
    
    /**
     * Limpa o registro de operações.
     */
    fun clearRecordedOperations() {
        _recordedOperations.clear()
    }
    
    /**
     * Verifica se navegou para uma rota específica.
     */
    fun hasNavigatedTo(route: String): Boolean {
        return _recordedOperations.any { 
            it is NavigationOperation.Navigate && it.destination.route == route 
        }
    }
    
    /**
     * Conta quantas navegações foram feitas.
     */
    fun countNavigations(): Int {
        return _recordedOperations.count { it is NavigationOperation.Navigate }
    }
    
    /**
     * Reseta o mock para o estado inicial.
     */
    fun reset(initialDestination: Destination? = null) {
        backStack.clear()
        initialDestination?.let { backStack.add(it) }
        results.clear()
        _recordedOperations.clear()
        shouldFail = false
        latencyMs = 0
    }
}

/**
 * Representa uma operação de navegação gravada.
 */
sealed class NavigationOperation {
    abstract val timestamp: Long
    
    data class Navigate(
        val destination: Destination,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
    
    data object NavigateBack : NavigationOperation() {
        override val timestamp: Long = currentTimeMillis()
    }
    
    data class NavigateBackTo(
        val destination: Destination,
        val inclusive: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
    
    data class GetCurrentDestination(
        val result: Destination?,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
    
    data class CanNavigateBack(
        val result: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
    
    data class SetResult(
        val key: String,
        val value: Any,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
    
    data class GetResult(
        val key: String,
        val value: Any?,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
    
    data class NavigateAndClearStack(
        val destination: Destination,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
    
    data class Replace(
        val destination: Destination,
        override val timestamp: Long = currentTimeMillis()
    ) : NavigationOperation()
}
