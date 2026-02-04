package io.flowmobile.core.services

import kotlin.reflect.KClass

/**
 * Registro tipado de serviços do host.
 * 
 * O TypedHostServiceRegistry permite o registro e recuperação de serviços
 * de forma type-safe, com suporte a:
 * - Registro por tipo (KClass)
 * - Verificação de disponibilidade
 * - Listagem de serviços disponíveis
 * - Integração com HostServices
 */
class TypedHostServiceRegistry {
    
    private val services = mutableMapOf<KClass<*>, Any>()
    private var hostServices: HostServices? = null
    
    /**
     * Configura o HostServices como fonte principal de serviços.
     *
     * @param hostServices Instância de HostServices
     */
    fun configure(hostServices: HostServices) {
        this.hostServices = hostServices
        
        // Registra automaticamente os serviços disponíveis
        hostServices.storage?.let { register(StorageService::class, it) }
        hostServices.network?.let { register(NetworkService::class, it) }
        hostServices.logging?.let { register(LoggingService::class, it) }
        hostServices.analytics?.let { register(AnalyticsService::class, it) }
        hostServices.navigation?.let { register(NavigationService::class, it) }
        hostServices.notification?.let { register(NotificationService::class, it) }
        hostServices.device?.let { register(DeviceService::class, it) }
        
        // Registra serviços customizados
        hostServices.custom.forEach { (_, service) ->
            register(CustomService::class, service)
        }
    }
    
    /**
     * Registra um serviço no registry.
     *
     * @param type Tipo do serviço (interface)
     * @param service Implementação do serviço
     */
    fun <T : Any> register(type: KClass<T>, service: T) {
        services[type] = service
    }
    
    /**
     * Obtém um serviço pelo tipo.
     *
     * @return O serviço ou null se não estiver disponível
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): T? {
        return services[type] as? T
    }
    
    /**
     * Obtém um serviço pelo tipo, lançando exceção se não disponível.
     *
     * @return O serviço
     * @throws ServiceNotAvailableException se o serviço não estiver disponível
     */
    fun <T : Any> getOrThrow(type: KClass<T>): T {
        return get(type)
            ?: throw ServiceNotAvailableException(
                "Serviço ${type.simpleName} não está disponível"
            )
    }
    
    /**
     * Verifica se um serviço está disponível.
     *
     * @return true se o serviço está registrado
     */
    fun <T : Any> isAvailable(type: KClass<T>): Boolean {
        return services.containsKey(type)
    }
    
    /**
     * Remove um serviço do registry.
     *
     * @param type Tipo do serviço a remover
     */
    fun <T : Any> unregister(type: KClass<T>) {
        services.remove(type)
    }
    
    /**
     * Lista todos os tipos de serviços disponíveis.
     *
     * @return Lista de tipos de serviços registrados
     */
    fun listAvailable(): List<KClass<*>> {
        return services.keys.toList()
    }
    
    /**
     * Lista nomes dos serviços disponíveis.
     *
     * @return Lista de nomes de serviços
     */
    fun listAvailableNames(): List<String> {
        return services.keys.mapNotNull { it.simpleName }
    }
    
    /**
     * Limpa todos os serviços registrados.
     */
    fun clear() {
        services.clear()
        hostServices = null
    }
    
    /**
     * Retorna o HostServices configurado.
     */
    fun getHostServices(): HostServices? = hostServices
    
    /**
     * Verifica se o registry foi configurado com HostServices.
     */
    fun isConfigured(): Boolean = hostServices != null
    
    // Métodos de conveniência para serviços específicos
    
    /**
     * Obtém o StorageService.
     */
    fun getStorageService(): StorageService? = get(StorageService::class)
    
    /**
     * Obtém o NetworkService.
     */
    fun getNetworkService(): NetworkService? = get(NetworkService::class)
    
    /**
     * Obtém o LoggingService.
     */
    fun getLoggingService(): LoggingService? = get(LoggingService::class)
    
    /**
     * Obtém o AnalyticsService.
     */
    fun getAnalyticsService(): AnalyticsService? = get(AnalyticsService::class)
    
    /**
     * Obtém o NavigationService.
     */
    fun getNavigationService(): NavigationService? = get(NavigationService::class)
    
    /**
     * Obtém o NotificationService.
     */
    fun getNotificationService(): NotificationService? = get(NotificationService::class)
    
    /**
     * Obtém o DeviceService.
     */
    fun getDeviceService(): DeviceService? = get(DeviceService::class)
}

/**
 * Extensões inline reified para acesso type-safe.
 */
inline fun <reified T : Any> TypedHostServiceRegistry.get(): T? = get(T::class)

inline fun <reified T : Any> TypedHostServiceRegistry.getOrThrow(): T = getOrThrow(T::class)

inline fun <reified T : Any> TypedHostServiceRegistry.isAvailable(): Boolean = isAvailable(T::class)

inline fun <reified T : Any> TypedHostServiceRegistry.register(service: T) = register(T::class, service)

inline fun <reified T : Any> TypedHostServiceRegistry.unregister() = unregister(T::class)

/**
 * Exceção lançada quando um serviço não está disponível.
 */
class ServiceNotAvailableException(message: String) : RuntimeException(message)
