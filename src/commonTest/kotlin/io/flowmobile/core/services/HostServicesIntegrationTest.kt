package io.flowmobile.core.services

import io.flowmobile.core.domain.*
import io.flowmobile.core.runtime.*
import io.flowmobile.core.services.mock.*
import kotlin.test.*

/**
 * Testes de integração entre Host Services e o FlowExecutor.
 */
class HostServicesIntegrationTest {
    
    private lateinit var mockServices: MockHostServices
    private lateinit var registry: TypedHostServiceRegistry
    
    @BeforeTest
    fun setup() {
        mockServices = MockHostServices.createAll()
        registry = TypedHostServiceRegistry()
        registry.configure(mockServices.toHostServices())
    }
    
    @Test
    fun testRegistryConfiguredCorrectly() {
        assertTrue(registry.isConfigured())
        assertTrue(registry.isAvailable(StorageService::class))
        assertTrue(registry.isAvailable(NetworkService::class))
        assertTrue(registry.isAvailable(LoggingService::class))
    }
    
    @Test
    fun testMockStorageIntegration() = runTest {
        val storage = registry.getStorageService()!!
        
        // Simula operações que o executor faria
        storage.set("flow_state", "running")
        storage.set("current_step", "1")
        
        assertEquals("running", storage.get("flow_state"))
        assertEquals("1", storage.get("current_step"))
        
        // Verifica operações gravadas
        val mockStorage = mockServices.storage!!
        assertTrue(mockStorage.hasOperation { it is StorageOperation.Set })
        assertEquals(4, mockStorage.recordedOperations.size) // 2 sets + 2 gets
    }
    
    @Test
    fun testMockNetworkIntegration() = runTest {
        val network = registry.getNetworkService()!!
        
        // Configura resposta pré-definida
        mockServices.network!!.addPredefinedResponse(
            "https://api.flowmobile.io/execute",
            NetworkResponse.success(200, """{"status": "success", "result": 42}""")
        )
        
        // Simula requisição que um ActionComponent faria
        val response = network.request(RequestConfig(
            url = "https://api.flowmobile.io/execute",
            method = HttpMethod.POST,
            body = """{"action": "calculate"}"""
        ))
        
        assertTrue(response.isSuccess)
        assertEquals(200, response.statusCode)
        assertTrue(response.body?.contains("success") == true)
        
        // Verifica gravação
        assertTrue(mockServices.network!!.hasRequestTo("https://api.flowmobile.io/execute"))
    }
    
    @Test
    fun testMockLoggingIntegration() {
        val logging = registry.getLoggingService()!!
        
        // Simula logs que o executor geraria
        logging.info("FlowExecutor", "Iniciando execução do fluxo")
        logging.debug("FlowExecutor", "Componente START executado")
        logging.info("FlowExecutor", "Fluxo finalizado com sucesso")
        
        val mockLogging = mockServices.logging!!
        assertEquals(3, mockLogging.logEntries.size)
        assertTrue(mockLogging.hasLogWithMessage("Iniciando execução"))
    }
    
    @Test
    fun testMockAnalyticsIntegration() = runTest {
        val analytics = registry.getAnalyticsService()!!
        
        // Simula eventos que o executor emitiria
        analytics.trackEvent(AnalyticsEvent.FLOW_STARTED, mapOf(
            "flow_id" to "test_flow",
            "flow_name" to "Test Flow"
        ))
        
        analytics.trackEvent(AnalyticsEvent.COMPONENT_EXECUTED, mapOf(
            "component_id" to "start_1",
            "component_type" to "START"
        ))
        
        analytics.trackEvent(AnalyticsEvent.FLOW_COMPLETED, mapOf(
            "flow_id" to "test_flow",
            "duration_ms" to "150"
        ))
        
        val mockAnalytics = mockServices.analytics!!
        assertTrue(mockAnalytics.hasEvent(AnalyticsEvent.FLOW_STARTED))
        assertTrue(mockAnalytics.hasEvent(AnalyticsEvent.FLOW_COMPLETED))
        assertEquals(3, mockAnalytics.countEvents())
    }
    
    @Test
    fun testMockNavigationIntegration() = runTest {
        val navigation = registry.getNavigationService()!!
        
        // Simula navegação que um ActionComponent faria
        val result = navigation.navigate(Destination(
            route = "result_screen",
            arguments = mapOf(
                "flow_result" to "success",
                "output_value" to "42"
            )
        ))
        
        assertTrue(result is NavigationResult.Success)
        assertEquals("result_screen", navigation.getCurrentDestination()?.route)
        
        val mockNavigation = mockServices.navigation!!
        assertTrue(mockNavigation.hasNavigatedTo("result_screen"))
    }
    
    @Test
    fun testFailureInjectionScenario() = runTest {
        // Configura para simular falha de rede
        mockServices.network!!.shouldFail = true
        mockServices.network!!.failureMessage = "Timeout de conexão"
        
        val network = registry.getNetworkService()!!
        
        val response = network.request(RequestConfig(url = "https://api.test.com/action"))
        
        assertFalse(response.isSuccess)
        assertTrue(response.error?.contains("Timeout") == true)
    }
    
    @Test
    fun testServiceNotAvailableHandling() {
        // Cria registry com apenas storage
        val minimalRegistry = TypedHostServiceRegistry()
        minimalRegistry.configure(MockHostServicesFactory.createMinimal())
        
        // Storage deve estar disponível
        assertTrue(minimalRegistry.isAvailable(StorageService::class))
        
        // Analytics não deve estar disponível
        assertFalse(minimalRegistry.isAvailable(AnalyticsService::class))
        assertNull(minimalRegistry.getAnalyticsService())
        
        // getOrThrow deve lançar exceção
        assertFailsWith<ServiceNotAvailableException> {
            minimalRegistry.getOrThrow(AnalyticsService::class)
        }
    }
    
    @Test
    fun testDeviceInfoForContextEnrichment() = runTest {
        val device = registry.getDeviceService()!!
        
        // Simula coleta de info do device para enriquecer contexto de execução
        val deviceInfo = device.getDeviceInfo()
        val appInfo = device.getAppInfo()
        val locale = device.getLocale()
        
        assertEquals(Platform.ANDROID, deviceInfo.platform)
        assertEquals("FlowMobile Mock", appInfo.appName)
        assertEquals("pt-BR", locale)
        
        // Verifica gravação
        val mockDevice = mockServices.device!!
        assertTrue(mockDevice.recordedOperations.any { it is DeviceOperation.GetDeviceInfo })
    }
    
    @Test
    fun testNotificationFromFlow() = runTest {
        val notification = registry.getNotificationService()!!
        
        // Simula notificação que um ActionComponent poderia disparar
        val notificationId = notification.show(NotificationConfig(
            title = "Fluxo Concluído",
            body = "O processamento foi finalizado com sucesso.",
            data = mapOf("flow_id" to "test_123")
        ))
        
        assertNotNull(notificationId)
        
        val mockNotification = mockServices.notification!!
        assertTrue(mockNotification.displayedNotifications.isNotEmpty())
        assertTrue(mockNotification.hasNotification(notificationId))
    }
    
    @Test
    fun testCompleteFlowSimulation() = runTest {
        val storage = registry.getStorageService()!!
        val logging = registry.getLoggingService()!!
        val analytics = registry.getAnalyticsService()!!
        
        // Simula execução completa de um fluxo
        
        // 1. Início
        logging.info("FlowExecutor", "Iniciando fluxo: checkout_flow")
        analytics.trackEvent(AnalyticsEvent.FLOW_STARTED, mapOf("flow_id" to "checkout_flow"))
        
        // 2. Carrega dados do storage
        storage.set("cart_items", """["item1", "item2"]""")
        val cartItems = storage.get("cart_items")
        logging.debug("FlowExecutor", "Cart items carregados: $cartItems")
        
        // 3. Processa ação
        analytics.trackEvent(AnalyticsEvent.ACTION_INVOKED, mapOf(
            "action" to "process_payment",
            "amount" to "199.99"
        ))
        
        // 4. Salva resultado
        storage.set("payment_status", "completed")
        storage.set("transaction_id", "TXN_12345")
        
        // 5. Finaliza
        analytics.trackEvent(AnalyticsEvent.FLOW_COMPLETED, mapOf(
            "flow_id" to "checkout_flow",
            "status" to "success"
        ))
        logging.info("FlowExecutor", "Fluxo checkout_flow finalizado")
        
        // Verificações
        val mockStorage = mockServices.storage!!
        val mockLogging = mockServices.logging!!
        val mockAnalytics = mockServices.analytics!!
        
        assertEquals("completed", mockStorage.getCurrentState()["payment_status"])
        assertTrue(mockLogging.hasLogWithMessage("checkout_flow"))
        assertTrue(mockAnalytics.hasEvent(AnalyticsEvent.FLOW_STARTED))
        assertTrue(mockAnalytics.hasEvent(AnalyticsEvent.FLOW_COMPLETED))
    }
    
    @Test
    fun testResetAllMocksForNewTest() = runTest {
        // Executa algumas operações
        mockServices.storage?.set("key", "value")
        mockServices.logging?.info("TAG", "message")
        mockServices.analytics?.trackEvent("event")
        
        // Reseta todos
        mockServices.resetAll()
        
        // Verifica que tudo foi limpo
        assertTrue(mockServices.storage?.recordedOperations?.isEmpty() == true)
        assertTrue(mockServices.logging?.logEntries?.isEmpty() == true)
        assertTrue(mockServices.analytics?.events?.isEmpty() == true)
    }
}

// Helper para executar testes com coroutines
private fun runTest(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest { block() }
}
