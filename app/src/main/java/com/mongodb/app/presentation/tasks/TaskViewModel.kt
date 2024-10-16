package com.mongodb.app.presentation.tasks

import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.mongodb.app.data.InitialResults
import com.mongodb.app.data.ResultsChange
import com.mongodb.app.data.SubscriptionType
import com.mongodb.app.data.SyncRepository
import com.mongodb.app.data.UpdatedResults
import com.mongodb.app.domain.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

object TaskViewEvent

class TaskViewModel(
    private val repository: SyncRepository,
    val taskListState: SnapshotStateList<Item> = mutableStateListOf()
) : ViewModel() {

    private val _event: MutableSharedFlow<TaskViewEvent> = MutableSharedFlow()
    val event: Flow<TaskViewEvent>
        get() = _event
    private var subscriptionJob: Job? = null

    init {
        viewModelScope.launch {
            subscriptionJob = viewModelScope.launch {
                getTaskList(SubscriptionType.MINE)
            }
        }
    }

    fun updateQuerySubscriptionModel(updatedSubscriptionType: SubscriptionType) {
        subscriptionJob?.cancel()  // Cancel the previous job
        subscriptionJob = viewModelScope.launch {
            getTaskList(updatedSubscriptionType)
        }
    }

    private suspend fun getTaskList(subscriptionType: SubscriptionType) {
        repository.getTaskList(subscriptionType)
            .collect { event: ResultsChange<Item> ->
                when (event) {
                    is InitialResults -> {
                        taskListState.clear()
                        taskListState.addAll(event.list)
                    }
                    is UpdatedResults -> {
                        if (event.deletions.isNotEmpty() && taskListState.isNotEmpty()) {
                            event.deletions.reversed().forEach {
                                val currentItem = taskListState.find { task -> task.id == it.id }
                                taskListState.remove(currentItem)
                            }
                        }
                        if (event.insertions.isNotEmpty()) {
                            event.insertions.forEach {
                                taskListState.add(it)
                            }
                        }
                        if (event.changes.isNotEmpty()) {
                            event.changes.forEach {
                                val currentItem = taskListState.find { task -> task.id == it.id }
                                val index = taskListState.indexOf(currentItem)
                                taskListState[index] = it
                            }
                        }
                    }
                    else -> Unit // No-op
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
