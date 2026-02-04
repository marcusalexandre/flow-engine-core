package io.flowmobile.core.loading

import kotlinx.serialization.Serializable

/**
 * Resultado de uma operação de carregamento ou parsing.
 * Pode ser sucesso com valor ou falha com erros detalhados.
 *
 * @param T Tipo do valor em caso de sucesso
 */
@Serializable
sealed class LoadResult<out T> {
    
    /**
     * Carregamento bem-sucedido com o valor resultante.
     *
     * @property value O valor carregado
     * @property warnings Lista de avisos (não bloqueantes)
     */
    @Serializable
    data class Success<T>(
        val value: T,
        val warnings: List<LoadWarning> = emptyList()
    ) : LoadResult<T>()
    
    /**
     * Carregamento falhou com erros.
     *
     * @property errors Lista de erros encontrados
     */
    @Serializable
    data class Failure<T>(
        val errors: List<LoadError>
    ) : LoadResult<T>() {
        constructor(error: LoadError) : this(listOf(error))
        constructor(message: String) : this(LoadError(message = message))
    }
    
    /**
     * Retorna true se o resultado é sucesso.
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Retorna true se o resultado é falha.
     */
    fun isFailure(): Boolean = this is Failure
    
    /**
     * Retorna o valor se sucesso, ou null se falha.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    
    /**
     * Retorna o valor se sucesso, ou lança exceção se falha.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw LoadException(errors)
    }
    
    /**
     * Retorna os erros se falha, ou lista vazia se sucesso.
     */
    fun errorsOrEmpty(): List<LoadError> = when (this) {
        is Success -> emptyList()
        is Failure -> errors
    }
    
    /**
     * Transforma o valor se sucesso.
     */
    inline fun <R> map(transform: (T) -> R): LoadResult<R> = when (this) {
        is Success -> Success(transform(value), warnings)
        is Failure -> Failure(errors)
    }
    
    /**
     * Transforma o valor se sucesso, retornando outro LoadResult.
     */
    inline fun <R> flatMap(transform: (T) -> LoadResult<R>): LoadResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> Failure(errors)
    }
    
    companion object {
        /**
         * Cria um resultado de sucesso.
         */
        fun <T> success(value: T, warnings: List<LoadWarning> = emptyList()): LoadResult<T> =
            Success(value, warnings)
        
        /**
         * Cria um resultado de falha.
         */
        fun <T> failure(errors: List<LoadError>): LoadResult<T> =
            Failure(errors)
        
        /**
         * Cria um resultado de falha com um único erro.
         */
        fun <T> failure(error: LoadError): LoadResult<T> =
            Failure(listOf(error))
        
        /**
         * Cria um resultado de falha com mensagem simples.
         */
        fun <T> failure(message: String): LoadResult<T> =
            Failure(LoadError(message = message))
    }
}

/**
 * Erro encontrado durante carregamento ou parsing.
 *
 * @property code Código do erro para identificação programática
 * @property message Mensagem descritiva do erro
 * @property line Linha no JSON onde o erro ocorreu (1-indexed)
 * @property column Coluna no JSON onde o erro ocorreu (1-indexed)
 * @property path Caminho JSON até o elemento com erro (ex: "flow.components[0].id")
 * @property details Detalhes adicionais sobre o erro
 */
@Serializable
data class LoadError(
    val code: String = "UNKNOWN_ERROR",
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val path: String? = null,
    val details: Map<String, String> = emptyMap()
) {
    override fun toString(): String {
        val location = when {
            line != null && column != null -> " at line $line, column $column"
            line != null -> " at line $line"
            path != null -> " at $path"
            else -> ""
        }
        return "[$code] $message$location"
    }
}

/**
 * Aviso encontrado durante carregamento (não bloqueante).
 *
 * @property code Código do aviso
 * @property message Mensagem descritiva
 * @property path Caminho JSON relacionado
 */
@Serializable
data class LoadWarning(
    val code: String,
    val message: String,
    val path: String? = null
) {
    override fun toString(): String {
        val location = path?.let { " at $it" } ?: ""
        return "[$code] $message$location"
    }
}

/**
 * Exceção lançada quando há erros de carregamento e o valor é solicitado.
 */
class LoadException(val errors: List<LoadError>) : Exception(
    "Falha ao carregar: ${errors.joinToString("; ")}"
)
