package io.flowmobile.core.runtime.performance

import io.flowmobile.core.domain.Component
import io.flowmobile.core.domain.Flow

/**
 * Cache para resolução de grafos e caminhos no fluxo.
 *
 * Esta classe implementa cache estruturado para reduzir o custo computacional
 * de operações frequentes como busca de componentes, resolução de conexões
 * e identificação de caminhos paralelos.
 *
 * Ganho esperado: 80% menos computação em operações de resolução de grafo.
 */
class GraphCache {
    
    // Cache de lookups de componentes: flowId + componentId -> Component
    private val componentCache = mutableMapOf<String, Component>()
    
    // Cache de conexões saintes: componentId -> List<Component>
    private val outgoingConnectionsCache = mutableMapOf<String, List<String>>()
    
    // Cache de conexões entrantes: componentId -> List<Component>
    private val incomingConnectionsCache = mutableMapOf<String, List<String>>()
    
    // Cache de caminhos paralelos: componentId -> List<List<String>>
    private val parallelPathsCache = mutableMapOf<String, List<List<String>>>()
    
    // Versão do cache - incrementa quando invalidado
    private var cacheVersion = 0L
    
    /**
     * Obtém um componente do cache ou calcula se não existir.
     */
    fun getComponent(
        flow: Flow,
        componentId: String,
        compute: () -> Component?
    ): Component? {
        val cacheKey = "${flow.id}:$componentId"
        return componentCache.getOrPut(cacheKey) {
            compute() ?: return null
        }
    }
    
    /**
     * Obtém conexões saintes em cache.
     */
    fun getOutgoingConnections(
        componentId: String,
        compute: () -> List<String>
    ): List<String> {
        return outgoingConnectionsCache.getOrPut(componentId) {
            compute()
        }
    }
    
    /**
     * Obtém conexões entrantes em cache.
     */
    fun getIncomingConnections(
        componentId: String,
        compute: () -> List<String>
    ): List<String> {
        return incomingConnectionsCache.getOrPut(componentId) {
            compute()
        }
    }
    
    /**
     * Obtém caminhos paralelos em cache.
     * Útil para otimizar Fork/Join execution.
     */
    fun getParallelPaths(
        componentId: String,
        compute: () -> List<List<String>>
    ): List<List<String>> {
        return parallelPathsCache.getOrPut(componentId) {
            compute()
        }
    }
    
    /**
     * Invalida todo o cache.
     * Deve ser chamado quando o grafo é modificado.
     */
    fun invalidate() {
        componentCache.clear()
        outgoingConnectionsCache.clear()
        incomingConnectionsCache.clear()
        parallelPathsCache.clear()
        cacheVersion++
    }
    
    /**
     * Invalida cache específico para um componente.
     */
    fun invalidateComponent(componentId: String) {
        outgoingConnectionsCache.remove(componentId)
        incomingConnectionsCache.remove(componentId)
        parallelPathsCache.remove(componentId)
        cacheVersion++
    }
    
    /**
     * Retorna a versão atual do cache.
     * Útil para detectar quando o cache foi alterado.
     */
    fun getVersion(): Long = cacheVersion
}
