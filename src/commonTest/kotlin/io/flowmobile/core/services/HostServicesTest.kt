package io.flowmobile.core.services

import io.flowmobile.core.services.mock.*
import kotlin.test.*

/**
 * Testes para MockStorageService.
 */
class MockStorageServiceTest {
    
    private lateinit var storage: MockStorageService
    
    @BeforeTest
    fun setup() {
        storage = MockStorageService()
    }
    
    @Test
    fun testSetAndGet() = runTest {
        storage.set("key1", "value1")
        
        val result = storage.get("key1")
        
        assertEquals("value1", result)
    }
    
    @Test
    fun testGetNonExistentKey() = runTest {
        val result = storage.get("nonexistent")
        
        assertNull(result)
    }
    
    @Test
    fun testRemove() = runTest {
        storage.set("key1", "value1")
        
        val existed = storage.remove("key1")
        val result = storage.get("key1")
        
        assertTrue(existed)
        assertNull(result)
    }
    
    @Test
    fun testRemoveNonExistent() = runTest {
        val existed = storage.remove("nonexistent")
        
        assertFalse(existed)
    }
    
    @Test
    fun testClear() = runTest {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        
        storage.clear()
        
        assertTrue(storage.keys().isEmpty())
    }
    
    @Test
    fun testKeys() = runTest {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        
        val keys = storage.keys()
        
        assertEquals(setOf("key1", "key2"), keys)
    }
    
    @Test
    fun testContains() = runTest {
        storage.set("key1", "value1")
        
        assertTrue(storage.contains("key1"))
        assertFalse(storage.contains("key2"))
    }
    
    @Test
    fun testPredefinedValues() = runTest {
        val storageWithPredefined = MockStorageService(
            predefinedValues = mapOf("predef" to "predefined_value")
        )
        
        val result = storageWithPredefined.get("predef")
        
        assertEquals("predefined_value", result)
    }
    
    @Test
    fun testOperationRecording() = runTest {
        storage.set("key1", "value1")
        storage.get("key1")
        storage.remove("key1")
        
        val operations = storage.recordedOperations
        
        assertEquals(3, operations.size)
        assertTrue(operations[0] is StorageOperation.Set)
        assertTrue(operations[1] is StorageOperation.Get)
        assertTrue(operations[2] is StorageOperation.Remove)
    }
    
    @Test
    fun testFailureInjection() = runTest {
        storage.shouldFail = true
        
        val exception = assertFailsWith<MockServiceException> {
            storage.get("key1")
        }
        
        assertTrue(exception.message?.contains("Falha simulada") == true)
    }
    
    @Test
    fun testGetMultiple() = runTest {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        storage.set("key3", "value3")
        
        val result = storage.getMultiple(setOf("key1", "key3", "nonexistent"))
        
        assertEquals(mapOf("key1" to "value1", "key3" to "value3"), result)
    }
    
    @Test
    fun testSetMultiple() = runTest {
        storage.setMultiple(mapOf(
            "key1" to "value1",
            "key2" to "value2"
        ))
        
        assertEquals("value1", storage.get("key1"))
        assertEquals("value2", storage.get("key2"))
    }
    
    @Test
    fun testReset() = runTest {
        storage.set("key1", "value1")
        storage.shouldFail = true
        
        storage.reset()
        
        // Após reset, shouldFail deve ser false
        assertFalse(storage.shouldFail)
        // recordedOperations foi limpo
        assertTrue(storage.recordedOperations.isEmpty())
        // E a chave não existe mais (pois foi limpa)
        assertNull(storage.get("key1"))
    }
    
    @Test
    fun testGetCurrentState() = runTest {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        
        val state = storage.getCurrentState()
        
        assertEquals(mapOf("key1" to "value1", "key2" to "value2"), state)
    }
}

/**
 * Testes para MockNetworkService.
 */
class MockNetworkServiceTest {
    
    private lateinit var network: MockNetworkService
    
    @BeforeTest
    fun setup() {
        network = MockNetworkService()
    }
    
    @Test
    fun testDefaultSuccessResponse() = runTest {
        val config = RequestConfig(url = "https://api.example.com/test")
        
        val response = network.request(config)
        
        assertTrue(response.isSuccess)
        assertEquals(200, response.statusCode)
    }
    
    @Test
    fun testPredefinedResponse() = runTest {
        val predefinedResponse = NetworkResponse.success(
            statusCode = 201,
            body = """{"id": 123}"""
        )
        network = MockNetworkService(
            predefinedResponses = mapOf("https://api.example.com/create" to predefinedResponse)
        )
        
        val response = network.request(RequestConfig(url = "https://api.example.com/create"))
        
        assertEquals(201, response.statusCode)
        assertEquals("""{"id": 123}""", response.body)
    }
    
    @Test
    fun testWildcardResponse() = runTest {
        val predefinedResponse = NetworkResponse.success(statusCode = 200, body = "matched")
        network = MockNetworkService(
            predefinedResponses = mapOf("https://api.example.com/*" to predefinedResponse)
        )
        
        val response = network.request(RequestConfig(url = "https://api.example.com/any/path"))
        
        assertEquals("matched", response.body)
    }
    
    @Test
    fun testConnectionFailure() = runTest {
        network.shouldFail = true
        
        val response = network.request(RequestConfig(url = "https://api.example.com/test"))
        
        assertFalse(response.isSuccess)
        assertEquals(-1, response.statusCode)
    }
    
    @Test
    fun testNoConnection() = runTest {
        network.isConnectedState = false
        
        val response = network.request(RequestConfig(url = "https://api.example.com/test"))
        
        assertFalse(response.isSuccess)
    }
    
    @Test
    fun testIsConnected() = runTest {
        network.isConnectedState = true
        assertTrue(network.isConnected())
        
        network.isConnectedState = false
        assertFalse(network.isConnected())
    }
    
    @Test
    fun testConnectionType() = runTest {
        network.connectionTypeState = ConnectionType.WIFI
        assertEquals(ConnectionType.WIFI, network.getConnectionType())
        
        network.connectionTypeState = ConnectionType.MOBILE
        assertEquals(ConnectionType.MOBILE, network.getConnectionType())
    }
    
    @Test
    fun testRequestRecording() = runTest {
        val config = RequestConfig(
            url = "https://api.example.com/test",
            method = HttpMethod.POST
        )
        
        network.request(config)
        
        assertTrue(network.hasRequestTo("https://api.example.com/test"))
        assertEquals(1, network.countRequestsByMethod(HttpMethod.POST))
    }
    
    @Test
    fun testDownload() = runTest {
        val result = network.download("https://example.com/file.txt")
        
        assertTrue(result is DownloadResult.Success)
        val success = result as DownloadResult.Success
        assertTrue(success.data.isNotEmpty())
    }
    
    @Test
    fun testDownloadFailure() = runTest {
        network.shouldFail = true
        
        val result = network.download("https://example.com/file.txt")
        
        assertTrue(result is DownloadResult.Failure)
    }
    
    @Test
    fun testCancel() = runTest {
        val cancelled = network.cancel("request_123")
        
        assertTrue(cancelled)
    }
    
    @Test
    fun testReset() = runTest {
        network.shouldFail = true
        network.isConnectedState = false
        network.request(RequestConfig(url = "https://test.com"))
        
        network.reset()
        
        assertFalse(network.shouldFail)
        assertTrue(network.isConnectedState)
        assertTrue(network.recordedRequests.isEmpty())
    }
}

/**
 * Testes para MockNavigationService.
 */
class MockNavigationServiceTest {
    
    private lateinit var navigation: MockNavigationService
    
    @BeforeTest
    fun setup() {
        navigation = MockNavigationService(
            initialDestination = Destination(route = "home")
        )
    }
    
    @Test
    fun testNavigate() = runTest {
        val result = navigation.navigate(Destination(route = "details"))
        
        assertTrue(result is NavigationResult.Success)
        assertEquals("details", (result as NavigationResult.Success).destination.route)
    }
    
    @Test
    fun testNavigateBack() = runTest {
        navigation.navigate(Destination(route = "details"))
        
        val result = navigation.navigateBack()
        
        assertTrue(result is NavigationResult.Success)
        assertEquals("home", (result as NavigationResult.Success).destination.route)
    }
    
    @Test
    fun testNavigateBackNoStack() = runTest {
        val result = navigation.navigateBack()
        
        assertTrue(result is NavigationResult.Failure)
        assertEquals(NavigationFailureReason.NO_BACK_STACK, (result as NavigationResult.Failure).reason)
    }
    
    @Test
    fun testGetCurrentDestination() = runTest {
        navigation.navigate(Destination(route = "details"))
        
        val current = navigation.getCurrentDestination()
        
        assertEquals("details", current?.route)
    }
    
    @Test
    fun testCanNavigateBack() = runTest {
        assertFalse(navigation.canNavigateBack())
        
        navigation.navigate(Destination(route = "details"))
        
        assertTrue(navigation.canNavigateBack())
    }
    
    @Test
    fun testSetAndGetResult() = runTest {
        navigation.setResult("resultKey", "resultValue")
        
        val result = navigation.getResult("resultKey")
        
        assertEquals("resultValue", result)
    }
    
    @Test
    fun testNavigateAndClearStack() = runTest {
        navigation.navigate(Destination(route = "page1"))
        navigation.navigate(Destination(route = "page2"))
        
        val result = navigation.navigateAndClearStack(Destination(route = "login"))
        
        assertTrue(result is NavigationResult.Success)
        assertEquals(1, navigation.getBackStack().size)
        assertEquals("login", navigation.getCurrentDestination()?.route)
    }
    
    @Test
    fun testReplace() = runTest {
        navigation.navigate(Destination(route = "details"))
        
        val result = navigation.replace(Destination(route = "replaced"))
        
        assertTrue(result is NavigationResult.Success)
        assertEquals(2, navigation.getBackStack().size)
        assertEquals("replaced", navigation.getCurrentDestination()?.route)
    }
    
    @Test
    fun testLaunchSingleTop() = runTest {
        navigation.navigate(Destination(
            route = "details",
            options = NavigationOptions(launchSingleTop = true)
        ))
        
        val stackSizeBefore = navigation.getBackStack().size
        
        navigation.navigate(Destination(
            route = "details",
            options = NavigationOptions(launchSingleTop = true)
        ))
        
        assertEquals(stackSizeBefore, navigation.getBackStack().size)
    }
    
    @Test
    fun testOperationRecording() = runTest {
        navigation.navigate(Destination(route = "details"))
        navigation.navigateBack()
        
        assertEquals(2, navigation.recordedOperations.size)
        assertTrue(navigation.hasNavigatedTo("details"))
    }
    
    @Test
    fun testFailureInjection() = runTest {
        navigation.shouldFail = true
        
        val result = navigation.navigate(Destination(route = "details"))
        
        assertTrue(result is NavigationResult.Failure)
    }
}

/**
 * Testes para MockLoggingService.
 */
class MockLoggingServiceTest {
    
    private lateinit var logging: MockLoggingService
    
    @BeforeTest
    fun setup() {
        logging = MockLoggingService()
    }
    
    @Test
    fun testLogLevels() {
        logging.verbose("TAG", "verbose message")
        logging.debug("TAG", "debug message")
        logging.info("TAG", "info message")
        logging.warning("TAG", "warning message")
        logging.error("TAG", "error message")
        logging.fatal("TAG", "fatal message")
        
        assertEquals(6, logging.logEntries.size)
        assertEquals(1, logging.countByLevel(LogLevel.VERBOSE))
        assertEquals(1, logging.countByLevel(LogLevel.ERROR))
    }
    
    @Test
    fun testMinLevel() {
        logging.setMinLevel(LogLevel.WARNING)
        
        logging.debug("TAG", "debug")
        logging.warning("TAG", "warning")
        logging.error("TAG", "error")
        
        assertEquals(2, logging.logEntries.size)
        assertFalse(logging.hasLogWithMessage("debug"))
        assertTrue(logging.hasLogWithMessage("warning"))
    }
    
    @Test
    fun testGetLogsByTag() {
        logging.info("TAG1", "message1")
        logging.info("TAG2", "message2")
        logging.info("TAG1", "message3")
        
        val tag1Logs = logging.getLogsByTag("TAG1")
        
        assertEquals(2, tag1Logs.size)
    }
    
    @Test
    fun testGetLogsByLevel() {
        logging.info("TAG", "info1")
        logging.error("TAG", "error1")
        logging.info("TAG", "info2")
        
        val infoLogs = logging.getLogsByLevel(LogLevel.INFO)
        
        assertEquals(2, infoLogs.size)
    }
    
    @Test
    fun testIsEnabled() {
        logging.setMinLevel(LogLevel.WARNING)
        
        assertFalse(logging.isEnabled(LogLevel.DEBUG))
        assertTrue(logging.isEnabled(LogLevel.WARNING))
        assertTrue(logging.isEnabled(LogLevel.ERROR))
    }
    
    @Test
    fun testDisableAll() {
        logging.setMinLevel(LogLevel.NONE)
        
        logging.fatal("TAG", "fatal")
        
        assertTrue(logging.logEntries.isEmpty())
    }
    
    @Test
    fun testWithMetadata() {
        logging.info("TAG", "message", mapOf("key" to "value"))
        
        val entry = logging.logEntries.first()
        assertEquals("value", entry.metadata["key"])
    }
    
    @Test
    fun testWithThrowable() {
        val exception = RuntimeException("test error")
        logging.error("TAG", "error occurred", exception)
        
        val entry = logging.logEntries.first()
        assertNotNull(entry.stackTrace)
        assertTrue(entry.stackTrace!!.contains("RuntimeException"))
    }
}

/**
 * Testes para MockAnalyticsService.
 */
class MockAnalyticsServiceTest {
    
    private lateinit var analytics: MockAnalyticsService
    
    @BeforeTest
    fun setup() {
        analytics = MockAnalyticsService()
    }
    
    @Test
    fun testTrackEvent() = runTest {
        analytics.trackEvent("button_click", mapOf("button_id" to "submit"))
        
        assertTrue(analytics.hasEvent("button_click"))
        assertEquals(1, analytics.countEvents())
    }
    
    @Test
    fun testTrackScreenView() = runTest {
        analytics.trackScreenView("HomeScreen", "HomeActivity")
        
        assertTrue(analytics.hasScreenView("HomeScreen"))
        assertEquals(1, analytics.screenViews.size)
    }
    
    @Test
    fun testUserProperties() = runTest {
        analytics.setUserProperty("subscription", "premium")
        analytics.setUserId("user_123")
        
        assertEquals("premium", analytics.userProperties["subscription"])
        assertEquals("user_123", analytics.userId)
    }
    
    @Test
    fun testResetUser() = runTest {
        analytics.setUserId("user_123")
        analytics.setUserProperty("prop", "value")
        
        analytics.resetUser()
        
        assertNull(analytics.userId)
        assertTrue(analytics.userProperties.isEmpty())
    }
    
    @Test
    fun testSession() = runTest {
        analytics.startSession("session_abc")
        
        assertEquals("session_abc", analytics.currentSessionId)
        assertTrue(analytics.hasEvent(AnalyticsEvent.SESSION_STARTED))
        
        analytics.endSession()
        
        assertNull(analytics.currentSessionId)
        assertTrue(analytics.hasEvent(AnalyticsEvent.SESSION_ENDED))
    }
    
    @Test
    fun testTrackConversion() = runTest {
        analytics.trackConversion("purchase", 99.99, "BRL")
        
        assertTrue(analytics.hasEvent("conversion"))
    }
    
    @Test
    fun testTrackError() = runTest {
        analytics.trackError("Network error", null, false)
        
        assertTrue(analytics.hasEvent(AnalyticsEvent.ERROR_OCCURRED))
    }
    
    @Test
    fun testDisabled() = runTest {
        analytics.setEnabled(false)
        
        analytics.trackEvent("test_event")
        
        assertFalse(analytics.hasEvent("test_event"))
    }
    
    @Test
    fun testGetEventsByName() = runTest {
        analytics.trackEvent("click", mapOf("target" to "button1"))
        analytics.trackEvent("click", mapOf("target" to "button2"))
        analytics.trackEvent("view")
        
        val clicks = analytics.getEventsByName("click")
        
        assertEquals(2, clicks.size)
    }
}

/**
 * Testes para TypedHostServiceRegistry.
 */
class TypedHostServiceRegistryTest {
    
    private lateinit var registry: TypedHostServiceRegistry
    
    @BeforeTest
    fun setup() {
        registry = TypedHostServiceRegistry()
    }
    
    @Test
    fun testRegisterAndGet() {
        val mockStorage = MockStorageService()
        
        registry.register(StorageService::class, mockStorage)
        
        val retrieved = registry.get(StorageService::class)
        assertSame(mockStorage, retrieved)
    }
    
    @Test
    fun testGetNonExistent() {
        val result = registry.get(StorageService::class)
        
        assertNull(result)
    }
    
    @Test
    fun testGetOrThrow() {
        val mockStorage = MockStorageService()
        registry.register(StorageService::class, mockStorage)
        
        val retrieved = registry.getOrThrow(StorageService::class)
        
        assertSame(mockStorage, retrieved)
    }
    
    @Test
    fun testGetOrThrowException() {
        assertFailsWith<ServiceNotAvailableException> {
            registry.getOrThrow(StorageService::class)
        }
    }
    
    @Test
    fun testIsAvailable() {
        assertFalse(registry.isAvailable(StorageService::class))
        
        registry.register(StorageService::class, MockStorageService())
        
        assertTrue(registry.isAvailable(StorageService::class))
    }
    
    @Test
    fun testUnregister() {
        registry.register(StorageService::class, MockStorageService())
        
        registry.unregister(StorageService::class)
        
        assertFalse(registry.isAvailable(StorageService::class))
    }
    
    @Test
    fun testListAvailable() {
        registry.register(StorageService::class, MockStorageService())
        registry.register(NetworkService::class, MockNetworkService())
        
        val available = registry.listAvailable()
        
        assertEquals(2, available.size)
    }
    
    @Test
    fun testClear() {
        registry.register(StorageService::class, MockStorageService())
        registry.register(NetworkService::class, MockNetworkService())
        
        registry.clear()
        
        assertTrue(registry.listAvailable().isEmpty())
    }
    
    @Test
    fun testConfigureWithHostServices() {
        val hostServices = MockHostServicesFactory.create {
            withStorage()
            withNetwork()
            withLogging()
        }
        
        registry.configure(hostServices)
        
        assertTrue(registry.isAvailable(StorageService::class))
        assertTrue(registry.isAvailable(NetworkService::class))
        assertTrue(registry.isAvailable(LoggingService::class))
        assertFalse(registry.isAvailable(AnalyticsService::class))
    }
    
    @Test
    fun testConvenienceMethods() {
        val hostServices = MockHostServicesFactory.createComplete()
        registry.configure(hostServices)
        
        assertNotNull(registry.getStorageService())
        assertNotNull(registry.getNetworkService())
        assertNotNull(registry.getLoggingService())
        assertNotNull(registry.getAnalyticsService())
        assertNotNull(registry.getNavigationService())
        assertNotNull(registry.getNotificationService())
        assertNotNull(registry.getDeviceService())
    }
}

/**
 * Testes para MockHostServicesFactory.
 */
class MockHostServicesFactoryTest {
    
    @Test
    fun testCreateMinimal() {
        val services = MockHostServicesFactory.createMinimal()
        
        assertNotNull(services.storage)
        assertNotNull(services.logging)
        assertNull(services.network)
        assertNull(services.analytics)
    }
    
    @Test
    fun testCreateComplete() {
        val services = MockHostServicesFactory.createComplete()
        
        assertNotNull(services.storage)
        assertNotNull(services.network)
        assertNotNull(services.logging)
        assertNotNull(services.analytics)
        assertNotNull(services.navigation)
        assertNotNull(services.notification)
        assertNotNull(services.device)
    }
    
    @Test
    fun testCreateWithConfiguration() {
        val services = MockHostServicesFactory.create {
            withStorage(predefinedValues = mapOf("key" to "value"))
            withNetwork()
        }
        
        assertNotNull(services.storage)
        assertNotNull(services.network)
        assertNull(services.logging)
    }
    
    @Test
    fun testWithLatency() {
        val config = MockHostServicesConfig()
            .withStorage()
            .withNetwork()
            .withLatency(100)
        
        assertEquals(100, config.storageService?.latencyMs)
        assertEquals(100, config.networkService?.latencyMs)
    }
    
    @Test
    fun testMockHostServicesContainer() {
        val mocks = MockHostServices.createAll()
        
        assertNotNull(mocks.storage)
        assertNotNull(mocks.network)
        assertNotNull(mocks.logging)
        
        val hostServices = mocks.toHostServices()
        assertNotNull(hostServices.storage)
    }
    
    @Test
    fun testResetAll() = runTest {
        val mocks = MockHostServices.createAll()
        mocks.storage?.set("key", "value")
        mocks.logging?.info("TAG", "message")
        
        mocks.resetAll()
        
        assertTrue(mocks.storage?.recordedOperations?.isEmpty() == true)
        assertTrue(mocks.logging?.logEntries?.isEmpty() == true)
    }
}

// Helper para executar testes com coroutines
private fun runTest(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest { block() }
}
