package com.mongodb.app.data

interface ResultsChange<T>

class InitialResults<T> : ResultsChange<T> {
    val list: MutableList<T> = mutableListOf()
}

class UpdatedResults<T> : ResultsChange<T> {
    val insertions: MutableList<T> = mutableListOf()
    val deletions: MutableList<T> = mutableListOf()
    val changes: MutableList<T> = mutableListOf()
}
