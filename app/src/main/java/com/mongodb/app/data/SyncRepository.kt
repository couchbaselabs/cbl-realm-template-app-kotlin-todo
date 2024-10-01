package com.mongodb.app.data

import com.couchbase.lite.Database
import com.couchbase.lite.Collection
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.DatabaseConfigurationFactory
import com.couchbase.lite.ListenerToken
import com.couchbase.lite.LogDomain
import com.couchbase.lite.LogLevel
import com.couchbase.lite.MutableDocument
import com.couchbase.lite.QueryChange
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorConfigurationFactory
import com.couchbase.lite.ReplicatorType
import com.couchbase.lite.URLEndpoint
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
    fun getTaskList(): Flow<List<Item>>


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
    private var _syncedItems = mapOf<String, Item>()

    init {
        //set Couchbase Lite logging levels
        Database.log.console.domains = LogDomain.ALL_DOMAINS
        Database.log.console.level = LogLevel.DEBUG
        //sanity check - user should never be null
        if (app.currentUser == null) {
            onError(IllegalStateException("User must be logged in to initialize the repository"))
        }
        try {
            val dbConfig =
                DatabaseConfigurationFactory.newConfig(app.filesDir)
            app.currentUser?.let { currentUser ->
                val username = currentUser.username
                    .replace("@", "-")
                    .lowercase(Locale.getDefault())
                this.database = Database("items${username}", dbConfig)
                this.collection = this.database.createCollection("items", "data")
                val replicatorConfig = ReplicatorConfigurationFactory
                    .newConfig(
                        target = URLEndpoint(URI(app.endpointUrl)),
                        type = ReplicatorType.PUSH_AND_PULL,
                        continuous = true
                    )
                replicatorConfig.collections.add(this.collection)
                this.replicator = Replicator(replicatorConfig)

                //setup status change listener in case of errors
                this.statusChangeToken = this.replicator.addChangeListener { change ->
                    val error: CouchbaseLiteException? = change.status.error
                    error?.let { e ->
                        onError(e)
                    }
                }
                this.replicator.start()
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    override suspend fun addTask(taskSummary: String) {
        if (app.currentUser == null) {
            throw IllegalStateException("User must be logged in to add a task")
        }
        app.currentUser?.let { user ->
            val task = Item(
                ownerId = user.username,
                summary = taskSummary
            )
            withContext(Dispatchers.IO){
                try {
                    val json = task.toJson()
                    val mutableDoc = MutableDocument(task.id, json)
                    collection.save(mutableDoc)
                }catch(e:Exception) {
                    onError(e)
                }
            }
        }
    }

    override fun close(){
        this.statusChangeToken.close()
        this.replicator.stop()
        this.database.close()
    }

    override suspend fun deleteTask(task: Item) {
        withContext(Dispatchers.IO) {
            try {
                val doc = collection.getDocument(task.id)
                doc?.let {
                    collection.delete(it)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    override fun getActiveSubscriptionType(): SubscriptionType {
        TODO("Not yet implemented")
    }

    override fun getTaskList(): Flow<List<Item>> {
        val query = this.database.createQuery("SELECT * FROM data.items as item ORDER BY META().id ASC")
        val flow = query
            .queryChangeFlow()
            .map { qc -> mapQueryChangeToItem(qc) }
            .flowOn(Dispatchers.IO)
        query.execute()
        return flow
    }

    override fun isTaskMine(task: Item): Boolean = task.ownerId == app.currentUser?.username

    private fun mapQueryChangeToItem(queryChange: QueryChange): List<Item> {
        val items = mutableListOf<Item>()
        queryChange.results?.let { results ->
            results.forEach { result ->
                val item = Json.decodeFromString<ItemDao>(result.toJSON()).item
                items.add(item)
            }
        }
        return items
    }

    override suspend fun toggleIsComplete(task: Item) {
        withContext(Dispatchers.IO) {
            try {
                val doc = collection.getDocument(task.id)
                doc?.let {
                    val isComplete = doc.getBoolean("isComplete")
                    val mutableDoc = doc.toMutable()
                    mutableDoc.setBoolean("isComplete", !isComplete )
                    collection.save(mutableDoc)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    override suspend fun updateSubscriptions(subscriptionType: SubscriptionType) {
        TODO("Not yet implemented")
    }

    override fun pauseSync() {
        this.replicator.stop()
    }

    override fun resumeSync() {
        this.replicator.start()
    }
}

/**
 * Mock repo for generating the Compose layout preview.
 */
 class MockRepository : SyncRepository {
    override fun getTaskList(): Flow<List<Item>> = flowOf()
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
