// ImportLog.kt

package com.mochyy.mochymusic.importer

// small enough for shared code and its platform actuals; a lost log line never fails an import
internal object ImportLog {
    fun d(message: String) = println("Import: $message")

    fun w(message: String, throwable: Throwable? = null) {
        println("Import: $message" + (throwable?.message?.let { ": $it" } ?: ""))
    }
}
