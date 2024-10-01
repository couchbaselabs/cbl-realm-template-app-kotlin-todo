package com.mongodb.app.presentation.tasks

import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.mongodb.app.data.SubscriptionType
import com.mongodb.app.data.SyncRepository
import com.mongodb.app.domain.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

object TaskViewEvent

class TaskViewModel constructor(
    private val repository: SyncRepository,
    val taskListState: SnapshotStateList<Item> = mutableStateListOf()
) : ViewModel() {

    private val _event: MutableSharedFlow<TaskViewEvent> = MutableSharedFlow()
    val event: Flow<TaskViewEvent>
        get() = _event

    init {
        viewModelScope.launch {
            repository.getTaskList(SubscriptionType.MINE)
                .collect { event: List<Item> ->
                    taskListState.clear()
                    taskListState.addAll(event)
                }
        }
    }

    fun updateQuerySubscriptionModel(updatedSubscriptionType: SubscriptionType) {
        viewModelScope.launch {
            repository.getTaskList(updatedSubscriptionType)
                .collect { event: List<Item> ->
                    taskListState.clear()
                    taskListState.addAll(event)
                }
        }
    }

    fun toggleIsComplete(task: Item) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.toggleIsComplete(task)
        }
    }

    fun showPermissionsMessage() {
        viewModelScope.launch {
            _event.emit(TaskViewEvent)
        }
    }

    fun isTaskMine(task: Item): Boolean = repository.isTaskMine(task)

    companion object {
        fun factory(
            repository: SyncRepository,
            owner: SavedStateRegistryOwner,
            defaultArgs: Bundle? = null
        ): AbstractSavedStateViewModelFactory {
            return object : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
                override fun <T : ViewModel> create(
                    key: String,
                    modelClass: Class<T>,
                    handle: SavedStateHandle
                ): T {
                    return TaskViewModel(repository) as T
                }
            }
        }
    }
}
