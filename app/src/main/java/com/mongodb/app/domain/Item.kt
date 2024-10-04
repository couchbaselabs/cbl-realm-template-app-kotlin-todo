package com.mongodb.app.domain

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.util.UUID

@Keep
@Serializable
data class ItemDao(var item: Item)

@Keep
@Serializable
data class Item(
    var id: String = UUID.randomUUID().toString(),
    var isComplete: Boolean = false,
    var summary: String = "",
    var ownerId: String = "") {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Item) return false

        return id == other.id &&
                isComplete == other.isComplete &&
                summary == other.summary &&
                ownerId == other.ownerId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + ownerId.hashCode()
        return result
    }

    fun toJson(): String {
        return Json.encodeToString(serializer(), this)
    }
}
