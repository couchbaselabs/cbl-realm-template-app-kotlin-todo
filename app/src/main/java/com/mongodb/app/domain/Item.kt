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

    fun toJson(): String {
        return Json.encodeToString(serializer(), this)
    }
}
