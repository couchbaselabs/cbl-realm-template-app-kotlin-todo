package com.mongodb.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
data class Item(
    @Transient var id: String = UUID.randomUUID().toString(),
    var isComplete: Boolean = false,
    var summary: String = "",
    var ownerId: String = "") {

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Item) return false
        if (this.id != other.id) return false
        if (this.isComplete != other.isComplete) return false
        if (this.summary != other.summary) return false
        if (this.ownerId != other.ownerId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + ownerId.hashCode()
        return result
    }
}
