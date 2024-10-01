package com.mongodb.app.data

interface ResultsChange<T> {
    val list: List<T>
}

interface InitialResults<T> : ResultsChange<T> { }
interface UpdatedResults<T> : ResultsChange<T> { }
