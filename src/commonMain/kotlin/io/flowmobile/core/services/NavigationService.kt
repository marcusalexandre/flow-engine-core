package io.flowmobile.core.services

import kotlinx.serialization.Serializable

/**
 * Serviço de navegação entre telas/rotas da aplicação.
 * 
 * Implementações específicas por plataforma:
 * - Android: Jetpack Navigation
 * - iOS: UIKit Navigation, SwiftUI Navigation
 * - Web: React Router, History API
 */
interface NavigationService {
    
    /**
     * Navega para um destino.
     *
     * @param destination Destino da navegação
     * @return Resultado da navegação
     */
    suspend fun navigate(destination: Destination): NavigationResult
    
    /**
     * Navega para trás (pop).
     *
     * @return Resultado da navegação
     */
    suspend fun navigateBack(): NavigationResult
    
    /**
     * Navega para trás até um destino específico.
     *
     * @param destination Destino até onde navegar de volta
     * @param inclusive Se deve incluir o destino no pop
     * @return Resultado da navegação
     */
    suspend fun navigateBackTo(destination: Destination, inclusive: Boolean = false): NavigationResult
    
    /**
     * Retorna o destino atual.
     *
     * @return Destino atual ou null se não houver
     */
    suspend fun getCurrentDestination(): Destination?
    
    /**
     * Verifica se é possível navegar para trás.
     *
     * @return true se há entradas na pilha de navegação
     */
    suspend fun canNavigateBack(): Boolean
    
    /**
     * Define um resultado para ser passado de volta à tela anterior.
     *
     * @param key Chave do resultado
     * @param value Valor do resultado
     */
    suspend fun setResult(key: String, value: Any)
    
    /**
     * Obtém um resultado passado pela tela seguinte.
     *
     * @param key Chave do resultado
     * @return Valor do resultado ou null se não existir
     */
    suspend fun getResult(key: String): Any?
    
    /**
     * Limpa toda a pilha de navegação e navega para um destino.
     *
     * @param destination Destino raiz
     * @return Resultado da navegação
     */
    suspend fun navigateAndClearStack(destination: Destination): NavigationResult
    
    /**
     * Substitui o destino atual por outro.
     *
     * @param destination Novo destino
     * @return Resultado da navegação
     */
    suspend fun replace(destination: Destination): NavigationResult
}

/**
 * Representa um destino de navegação.
 */
@Serializable
data class Destination(
    /** Identificador único da rota/tela */
    val route: String,
    
    /** Argumentos para passar ao destino */
    val arguments: Map<String, String> = emptyMap(),
    
    /** Opções de navegação */
    val options: NavigationOptions = NavigationOptions()
)

/**
 * Opções de navegação.
 */
@Serializable
data class NavigationOptions(
    /** Se deve criar nova tarefa (Android) ou não usar back stack */
    val clearTask: Boolean = false,
    
    /** Se deve usar animação de transição */
    val animated: Boolean = true,
    
    /** Tipo de transição */
    val transition: TransitionType = TransitionType.DEFAULT,
    
    /** Se deve manter estado ao navegar de volta */
    val saveState: Boolean = true,
    
    /** Se deve restaurar estado se o destino já existir */
    val restoreState: Boolean = true,
    
    /** Se deve fazer single top (não criar nova instância se já estiver no topo) */
    val launchSingleTop: Boolean = false
)

/**
 * Tipo de transição de navegação.
 */
@Serializable
enum class TransitionType {
    /** Transição padrão da plataforma */
    DEFAULT,
    
    /** Sem transição */
    NONE,
    
    /** Slide da direita */
    SLIDE_RIGHT,
    
    /** Slide de baixo */
    SLIDE_UP,
    
    /** Fade */
    FADE,
    
    /** Modal (iOS) */
    MODAL
}

/**
 * Resultado de uma operação de navegação.
 */
sealed class NavigationResult {
    /**
     * Navegação bem-sucedida.
     *
     * @param destination Destino alcançado
     */
    data class Success(val destination: Destination) : NavigationResult()
    
    /**
     * Navegação falhou.
     *
     * @param error Descrição do erro
     * @param reason Motivo da falha
     */
    data class Failure(val error: String, val reason: NavigationFailureReason) : NavigationResult()
}

/**
 * Motivo de falha de navegação.
 */
@Serializable
enum class NavigationFailureReason {
    /** Destino não encontrado */
    DESTINATION_NOT_FOUND,
    
    /** Não há entradas na pilha para voltar */
    NO_BACK_STACK,
    
    /** Navegação foi cancelada */
    CANCELLED,
    
    /** Erro de permissão */
    PERMISSION_DENIED,
    
    /** Erro desconhecido */
    UNKNOWN
}
