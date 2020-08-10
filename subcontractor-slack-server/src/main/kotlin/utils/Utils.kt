package utils

import java.util.concurrent.CompletableFuture


fun <T> MutableList<T>.swap(index1: Int, index2: Int) {
    val tmp = this[index1]
    this[index1] = this[index2]
    this[index2] = tmp
}

fun unreachable(): Nothing = error("Unreachable")
