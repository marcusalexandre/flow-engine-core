package io.flowmobile.core.services

import kotlinx.serialization.Serializable

/**
 * Serviço de logging estruturado.
 * 
 * Implementações específicas por plataforma:
 * - Android: Logcat, Timber
 * - iOS: os_log, CocoaLumberjack
 * - Web: console, custom loggers
 */
interface LoggingService {
    
    /**
     * Log nível VERBOSE.
     *
     * @param tag Tag para identificar a origem do log
     * @param message Mensagem do log
     * @param metadata Metadados adicionais
     */
    fun verbose(tag: String, message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log nível DEBUG.
     *
     * @param tag Tag para identificar a origem do log
     * @param message Mensagem do log
     * @param metadata Metadados adicionais
     */
    fun debug(tag: String, message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log nível INFO.
     *
     * @param tag Tag para identificar a origem do log
     * @param message Mensagem do log
     * @param metadata Metadados adicionais
     */
    fun info(tag: String, message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log nível WARNING.
     *
     * @param tag Tag para identificar a origem do log
     * @param message Mensagem do log
     * @param throwable Exceção associada (opcional)
     * @param metadata Metadados adicionais
     */
    fun warning(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log nível ERROR.
     *
     * @param tag Tag para identificar a origem do log
     * @param message Mensagem do log
     * @param throwable Exceção associada (opcional)
     * @param metadata Metadados adicionais
     */
    fun error(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log nível FATAL/ASSERT.
     *
     * @param tag Tag para identificar a origem do log
     * @param message Mensagem do log
     * @param throwable Exceção associada (opcional)
     * @param metadata Metadados adicionais
     */
    fun fatal(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log estruturado com nível configurável.
     *
     * @param entry Entrada de log estruturada
     */
    fun log(entry: LogEntry)
    
    /**
     * Define o nível mínimo de log a ser registrado.
     *
     * @param level Nível mínimo
     */
    fun setMinLevel(level: LogLevel)
    
    /**
     * Retorna o nível mínimo atual.
     *
     * @return Nível mínimo configurado
     */
    fun getMinLevel(): LogLevel
    
    /**
     * Verifica se um nível de log está habilitado.
     *
     * @param level Nível a verificar
     * @return true se o nível está habilitado
     */
    fun isEnabled(level: LogLevel): Boolean
}

/**
 * Nível de log.
 */
@Serializable
enum class LogLevel {
    /** Logs muito detalhados, geralmente só para debug profundo */
    VERBOSE,
    
    /** Logs de depuração */
    DEBUG,
    
    /** Informações gerais */
    INFO,
    
    /** Avisos que podem indicar problemas */
    WARNING,
    
    /** Erros que precisam de atenção */
    ERROR,
    
    /** Erros fatais que podem travar a aplicação */
    FATAL,
    
    /** Desabilita todos os logs */
    NONE
}

/**
 * Entrada de log estruturada.
 */
@Serializable
data class LogEntry(
    /** Nível do log */
    val level: LogLevel,
    
    /** Tag para identificar a origem */
    val tag: String,
    
    /** Mensagem principal */
    val message: String,
    
    /** Timestamp em milissegundos */
    val timestamp: Long,
    
    /** Nome da thread (se disponível) */
    val threadName: String? = null,
    
    /** Stacktrace de exceção (se houver) */
    val stackTrace: String? = null,
    
    /** Metadados adicionais em formato chave-valor */
    val metadata: Map<String, String> = emptyMap(),
    
    /** ID de correlação para rastreamento */
    val correlationId: String? = null
)
