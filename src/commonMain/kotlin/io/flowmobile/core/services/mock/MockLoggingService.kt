package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*

/**
 * Implementação mock do LoggingService para testes e sandbox.
 * Captura todos os logs para posterior verificação.
 */
class MockLoggingService(
    /** Nível mínimo de log inicial */
    private var minLevel: LogLevel = LogLevel.VERBOSE
) : LoggingService {
    
    private val _logEntries = mutableListOf<LogEntry>()
    
    /** Lista de logs gravados para verificação em testes */
    val logEntries: List<LogEntry> get() = _logEntries.toList()
    
    override fun verbose(tag: String, message: String, metadata: Map<String, Any>) {
        logInternal(LogLevel.VERBOSE, tag, message, null, metadata)
    }
    
    override fun debug(tag: String, message: String, metadata: Map<String, Any>) {
        logInternal(LogLevel.DEBUG, tag, message, null, metadata)
    }
    
    override fun info(tag: String, message: String, metadata: Map<String, Any>) {
        logInternal(LogLevel.INFO, tag, message, null, metadata)
    }
    
    override fun warning(tag: String, message: String, throwable: Throwable?, metadata: Map<String, Any>) {
        logInternal(LogLevel.WARNING, tag, message, throwable, metadata)
    }
    
    override fun error(tag: String, message: String, throwable: Throwable?, metadata: Map<String, Any>) {
        logInternal(LogLevel.ERROR, tag, message, throwable, metadata)
    }
    
    override fun fatal(tag: String, message: String, throwable: Throwable?, metadata: Map<String, Any>) {
        logInternal(LogLevel.FATAL, tag, message, throwable, metadata)
    }
    
    override fun log(entry: LogEntry) {
        if (isEnabled(entry.level)) {
            _logEntries.add(entry)
        }
    }
    
    override fun setMinLevel(level: LogLevel) {
        minLevel = level
    }
    
    override fun getMinLevel(): LogLevel = minLevel
    
    override fun isEnabled(level: LogLevel): Boolean {
        if (minLevel == LogLevel.NONE) return false
        return level.ordinal >= minLevel.ordinal
    }
    
    private fun logInternal(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
        metadata: Map<String, Any>
    ) {
        if (!isEnabled(level)) return
        
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            timestamp = currentTimeMillis(),
            stackTrace = throwable?.stackTraceToString(),
            metadata = metadata.mapValues { it.value.toString() }
        )
        _logEntries.add(entry)
    }
    
    // Métodos auxiliares para testes
    
    /**
     * Limpa os logs gravados.
     */
    fun clearLogs() {
        _logEntries.clear()
    }
    
    /**
     * Filtra logs por nível.
     */
    fun getLogsByLevel(level: LogLevel): List<LogEntry> {
        return _logEntries.filter { it.level == level }
    }
    
    /**
     * Filtra logs por tag.
     */
    fun getLogsByTag(tag: String): List<LogEntry> {
        return _logEntries.filter { it.tag == tag }
    }
    
    /**
     * Verifica se existe log com determinada mensagem.
     */
    fun hasLogWithMessage(message: String): Boolean {
        return _logEntries.any { it.message.contains(message) }
    }
    
    /**
     * Conta logs por nível.
     */
    fun countByLevel(level: LogLevel): Int {
        return _logEntries.count { it.level == level }
    }
    
    /**
     * Reseta o mock.
     */
    fun reset() {
        _logEntries.clear()
        minLevel = LogLevel.VERBOSE
    }
}
