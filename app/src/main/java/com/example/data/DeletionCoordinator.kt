package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One deletion lifecycle for every surface, including failed operations and retries. */
object DeletionCoordinator {
  enum class Stage { DISSOLVING, FAILED }
  private val _states = MutableStateFlow<Map<String, Stage>>(emptyMap())
  val states = _states.asStateFlow()
  private val retries = mutableMapOf<String, suspend () -> Unit>()

  suspend fun perform(key: String, action: suspend () -> Unit) {
    if (_states.value[key] == Stage.DISSOLVING) return
    retries[key] = action
    _states.update { it + (key to Stage.DISSOLVING) }
    try {
      // Let the selected item finish its dust/collapse before removing its model.
      delay(620)
      action()
      retries.remove(key)
      delay(80)
      _states.update { it - key }
    } catch (e: CancellationException) {
      _states.update { it - key }; throw e
    } catch (e: Exception) {
      _states.update { it + (key to Stage.FAILED) }; throw e
    }
  }
  suspend fun retry(key: String) { retries[key]?.let { perform(key, it) } }
  fun clear() { retries.clear(); _states.value = emptyMap() }
}
