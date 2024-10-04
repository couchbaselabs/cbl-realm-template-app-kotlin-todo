package com.mongodb.app.data

import com.couchbase.lite.BasicAuthenticator
import com.couchbase.lite.Database
import com.couchbase.lite.Collection
import com.couchbase.lite.CollectionConfiguration
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.DatabaseConfigurationFactory
import com.couchbase.lite.IndexBuilder
import com.couchbase.lite.ListenerToken
import com.couchbase.lite.LogDomain
import com.couchbase.lite.LogLevel
import com.couchbase.lite.MutableDocument
import com.couchbase.lite.Query
import com.couchbase.lite.QueryChange
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorConfigurationFactory
import com.couchbase.lite.ReplicatorType
import com.couchbase.lite.URLEndpoint
import com.couchbase.lite.ValueIndexItem
import com.couchbase.lite.newConfig
import com.couchbase.lite.queryChangeFlow
import com.mongodb.app.app
import com.mongodb.app.domain.Item
import com.mongodb.app.domain.ItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * Repository for accessing Capella App Services
 */
interface SyncRepository {

    /**
     * Adds a task that belongs to the current user using the specified [taskSummary].
     */
    suspend fun addTask(taskSummary: String)

    /**
     * Closes the replicator and database
     */
    fun close()

    /**
     * Deletes a given task.
     */
    suspend fun deleteTask(task: Item)

    /**
     * Returns the active [SubscriptionType].
     */
    fun getActiveSubscriptionType(): SubscriptionType

    /**
     * Returns a flow with the tasks for the current logged in user
     */
    fun getTaskList(subscriptionType: SubscriptionType): Flow<ResultsChange<Item>>

    /**
     * Whether the given [task] belongs to the current user logged in to the app.
     */
    fun isTaskMine(task: Item): Boolean

    /**
     * Pauses synchronization with Capella App Services. This is used to emulate a scenario of no connectivity.
     */
    fun pauseSync()

    /**
     * Resumes synchronization with Capella App Services
     */
    fun resumeSync()

    /**
     * Update the `isComplete` flag for a specific [Item].
     */
    suspend fun toggleIsComplete(task: Item)

    /**
     * Updates the Sync subscriptions based on the specified [SubscriptionType].
     */
    suspend fun updateSubscriptions(subscriptionType: SubscriptionType)

}

/**
 * Repo implementation used in runtime.
 */
class CouchbaseSyncRepository(
    private val onError: (error: Exception) -> Unit
) : SyncRepository {

    //used to mange the database, collections, and replicator
    private lateinit var collection: Collection
    private lateinit var database: Database
    private lateinit var replicator: Replicator
    private lateinit var statusChangeToken: ListenerToken
    private lateinit var queryMyTasks: Query
    private lateinit var queryAllTasks: Query
    private var _previousItemsMap: MutableMap<String, Item> = mutableMapOf()
    private var _currentSubscriptionType = SubscriptionType.MINE

    init {
        //set Couchbase Lite logging levels
        Database.log.console.domains = LogDomain.ALL_DOMAINS
        Database.log.console.level = LogLevel.DEBUG
        //sanity check - user should never be null
        if (app.currentUser == null) {
            onError(IllegalStateException("User must be logged in to initialize the repository"))
        }
        try {
            //setup database
            val dbConfig =
                DatabaseConfigurationFactory.newConfig(app.filesDir)
            app.currentUser?.let { currentUser ->
                val username = currentUser.username
                    .replace("@", "-")
                    .lowercase(Locale.getDefault())
                this.database = Database("items${username}", dbConfig)

                //create collection
                val checkCollection = this.database.getCollection("tasks", "data")
                if (checkCollection == null) {
                    this.collection = this.database.createCollection("tasks", "data")
                } else {
                    this.collection = checkCollection
                }

                //create index - check to see if it's created, if not create it
                val indexOwnerId = this.collection.getIndex("idxTasksOwnerId")
                if (indexOwnerId == null) {
                    val idxOwnerId = IndexBuilder.valueIndex(ValueIndexItem.property("ownerId"))
                    this.collection.createIndex("idxTasksOwnerId", idxOwnerId)
                }

                val replicatorConfig = ReplicatorConfigurationFactory
                    .newConfig(
                        target = URLEndpoint(URI(app.endpointUrl)),
                        type = ReplicatorType.PUSH_AND_PULL,
                        continuous = true
                    )

                //configure collections
                val collectionConfig = CollectionConfiguration()
                replicatorConfig.addCollection(collection, collectionConfig)

                //add authentication
                val auth =
                    BasicAuthenticator(currentUser.username, currentUser.password.toCharArray())
                replicatorConfig.authenticator = auth

                //create replicator
                this.replicator = Replicator(replicatorConfig)

                //setup status change listener in case of errors
                this.statusChangeToken = this.replicator.addChangeListener { change ->
                    val error: CouchbaseLiteException? = change.status.error
                    error?.let { e ->
                        onError(e)
                    }
                }
                this.replicator.start()

                //create cached queries
                var queryString = "SELECT * FROM data.tasks as item "
                this.queryAllTasks = this.database.createQuery(queryString)

                queryString += "WHERE item.ownerId = '${currentUser.username}' "
                queryString += "ORDER BY META().id ASC"
                this.queryMyTasks = this.database.createQuery(queryString)
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Adds a task that belongs to the current user using the specified [taskSummary].
     */
    override suspend fun addTask(taskSummary: String) {
        if (app.currentUser == null) {
            throw IllegalStateException("User must be logged in to add a task")
        }
        app.currentUser?.let { user ->
            val task = Item(
                ownerId = user.username,
                summary = taskSummary
            )
            withContext(Dispatchers.IO) {
                try {
                    val json = task.toJson()
                    val mutableDoc = MutableDocument(task.id, json)
                    collection.save(mutableDoc)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
    }

    /**
     * Closes the replicator and database
     */
    override fun close() {
        this.statusChangeToken.close()
        this.replicator.stop()
        this.database.close()
    }

    /**
     * Deletes a given task.
     */
    override suspend fun deleteTask(task: Item) {
        withContext(Dispatchers.IO) {
            try {
                val doc = collection.getDocument(task.id)
                doc?.let {
                    //handle security of the owner only being able to delete their own tasks
                    val ownerId = doc.getString("ownerId")
                    if (ownerId != task.ownerId) {
                        onError(IllegalStateException("User does not have permission to delete this task"))
                    } else {
                        collection.delete(it)
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Returns the active [SubscriptionType].
     */
    override fun getActiveSubscriptionType(): SubscriptionType {
        return _currentSubscriptionType
    }

    /**
     * Returns a flow with the tasks based on the subscription/mode selected
     */
    override fun getTaskList(subscriptionType: SubscriptionType): Flow<ResultsChange<Item>> {
        this._previousItemsMap = mutableMapOf()
        val query = when (subscriptionType) {
            SubscriptionType.MINE -> queryMyTasks
            SubscriptionType.ALL -> queryAllTasks
        }
        val flow = query
            .queryChangeFlow()
            .map { qc -> mapQueryChangeToItem(qc) }
            .flowOn(Dispatchers.IO)
        query.execute()
        return flow
    }

    /**
     * Whether the given [task] belongs to the current user logged in to the app.
     */
    override fun isTaskMine(task: Item): Boolean = task.ownerId == app.currentUser?.username

    /**
     * Map the query change to a list of items
     */
    private fun mapQueryChangeToItem(queryChange: QueryChange): ResultsChange<Item> {
        val isInitial = _previousItemsMap.isEmpty()
        val initialResults = InitialResults<Item>()
        val updatedResults = UpdatedResults<Item>()

        // used to track the current items which will
        // become the next previousItemMap after this is complete
        val currentItemsMap = mutableMapOf<String, Item>()

        // used to trim out items
        // anything left over is a deletion
        val previousItemsSet = _previousItemsMap.keys.toMutableSet()
        //loop through the query change results
        queryChange.results?.let { results ->
            results.forEach { result ->
                val item = Json.decodeFromString<ItemDao>(result.toJSON()).item
                currentItemsMap[item.id] = item
                if (isInitial) {
                    initialResults.list.add(item)
                } else {
                    val previousItem = _previousItemsMap[item.id]
                    when {
                        previousItem == null -> updatedResults.insertions.add(item)
                        item != previousItem -> updatedResults.changes.add(item)
                    }

                    previousItemsSet.remove(item.id)
                }
            }
        }
        if (!isInitial) {
            // Determine deletions
            previousItemsSet.forEach { id ->
                _previousItemsMap[id]?.let { updatedResults.deletions.add(it) }
            }
        }
        _previousItemsMap.clear()  // Clear previous map
        _previousItemsMap.putAll(currentItemsMap)  // Update _previousItemsMap with current items

        return if (isInitial) initialResults else updatedResults
    }

    /**
     * Update the `isComplete` flag for a specific [Item].
     */
    override suspend fun toggleIsComplete(task: Item) {
        withContext(Dispatchers.IO) {
            try {
                val doc = collection.getDocument(task.id)
                doc?.let {
                    //handle security of the owner only being able to update their own tasks
                    val ownerId = doc.getString("ownerId")
                    if (ownerId != task.ownerId) {
                        onError(IllegalStateException("User does not have permission to update this task"))
                    } else {
                        val isComplete = doc.getBoolean("isComplete")
                        val mutableDoc = doc.toMutable()
                        mutableDoc.setBoolean("isComplete", !isComplete)
                        collection.save(mutableDoc)
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Updates the subscription that is used in Query based on the specified [SubscriptionType].
     */
    override suspend fun updateSubscriptions(subscriptionType: SubscriptionType) {
        _currentSubscriptionType = subscriptionType
    }

    /**
     * Pauses synchronization with Capella App Services. This is used to emulate a scenario of no connectivity.
     */
    override fun pauseSync() {
        this.replicator.stop()
    }

    /**
     * Resumes synchronization with Capella App Services
     */
    override fun resumeSync() {
        this.replicator.start()
    }

}

/**
 * Mock repo for generating the Compose layout preview.
 */
class MockRepository : SyncRepository {
    override fun getTaskList(subscriptionType: SubscriptionType): Flow<ResultsChange<Item>> =
        flowOf()

    override suspend fun toggleIsComplete(task: Item) = Unit
    override suspend fun updateSubscriptions(subscriptionType: SubscriptionType) {
        TODO("Not yet implemented")
    }

    override suspend fun addTask(taskSummary: String) = Unit
    override suspend fun deleteTask(task: Item) = Unit
    override fun getActiveSubscriptionType(): SubscriptionType {
        TODO("Not yet implemented")
    }

    override fun pauseSync() = Unit
    override fun resumeSync() = Unit
    override fun isTaskMine(task: Item): Boolean = task.ownerId == MOCK_OWNER_ID_MINE
    override fun close() = Unit

    companion object {
        const val MOCK_OWNER_ID_MINE = "A"
        const val MOCK_OWNER_ID_OTHER = "B"

        fun getMockTask(index: Int): Item = Item().apply {
            this.summary = "Task $index"

            // Make every third task complete in preview
            this.isComplete = index % 3 == 0

            // Make every other task mine in preview
            this.ownerId = when {
                index % 2 == 0 -> MOCK_OWNER_ID_MINE
                else -> MOCK_OWNER_ID_OTHER
            }
        }
    }
}

/**
 * The two types of subscriptions according to item owner.
 */
enum class SubscriptionType {
    MINE, ALL
}
