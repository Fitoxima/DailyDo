package com.dailydo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel(private val dao: TodoDao) : ViewModel() {

    val todos: StateFlow<List<TodoItem>> = dao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val completedTodos: StateFlow<List<TodoItem>> = dao.getCompleted()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addTodo(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dao.insert(TodoItem(title = trimmed))
        }
    }

    suspend fun addTodoFull(item: TodoItem): Long {
        return dao.insert(item)
    }

    fun updateTodo(item: TodoItem) {
        viewModelScope.launch {
            dao.update(item)
        }
    }

    fun toggleDone(item: TodoItem) {
        viewModelScope.launch {
            dao.update(item.copy(isDone = !item.isDone))
        }
    }

    fun delete(item: TodoItem) {
        viewModelScope.launch {
            dao.delete(item)
        }
    }
}

class TodoViewModelFactory(private val dao: TodoDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            return TodoViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
