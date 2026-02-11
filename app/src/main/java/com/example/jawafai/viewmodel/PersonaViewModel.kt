package com.example.jawafai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jawafai.model.PersonaQuestions
import com.example.jawafai.repository.PersonaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for the Persona Settings screen.
 */
class PersonaViewModel(private val repository: PersonaRepository) : ViewModel() {

    /**
     * UI state for the Persona settings
     */
    sealed class UiState {
        object Loading : UiState()
        data class Success(val answers: Map<String, String>) : UiState()
        data class Error(val message: String) : UiState()
    }

    // Backing private state flow
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    // Public immutable state flow
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Holds the current answers (modified by user)
    private val _currentAnswers = MutableStateFlow<Map<String, String>>(emptyMap())
    val currentAnswers: StateFlow<Map<String, String>> = _currentAnswers.asStateFlow()

    init {
        loadPersona()
    }

    /**
     * Loads the persona data from the repository
     */
    fun loadPersona() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                repository.getPersonaFlow()
                    .catch { e ->
                        _uiState.value = UiState.Error(e.message ?: "Failed to load persona data")
                    }
                    .collect { personaData ->
                        _currentAnswers.value = personaData
                        _uiState.value = UiState.Success(personaData)
                    }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    /**
     * Updates a single answer
     * @param questionId The question ID
     * @param answer The new answer
     */
    fun updateAnswer(questionId: String, answer: String) {
        val updatedAnswers = _currentAnswers.value.toMutableMap().apply {
            this[questionId] = answer
        }
        _currentAnswers.value = updatedAnswers
    }

    /**
     * Validates all required questions have answers
     * @return true if valid, false otherwise
     */
    private fun validateAnswers(): Boolean {
        val requiredQuestions = PersonaQuestions.questions.filter { it.required }
        return requiredQuestions.all { question ->
            !_currentAnswers.value[question.id].isNullOrBlank()
        }
    }

    /**
     * Saves all persona answers to the repository
     * @return Result with success message or error
     */
    suspend fun savePersona(): Result<String> {
        return try {
            if (!validateAnswers()) {
                return Result.failure(IllegalStateException("Please answer all required questions"))
            }

            val success = repository.savePersona(_currentAnswers.value)
            if (success) {
                Result.success("Persona saved!")
            } else {
                Result.failure(Exception("Failed to save persona"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Factory for creating PersonaViewModel with its dependencies
 */
class PersonaViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PersonaViewModel::class.java)) {
            return PersonaViewModel(PersonaRepository.getInstance()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
