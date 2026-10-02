@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.app

import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

class IosBackgroundSaveExecution : BackgroundSaveExecution {
    override suspend fun <T> run(block: suspend () -> T): T {
        val application = UIApplication.sharedApplication
        var taskIdentifier = UIBackgroundTaskInvalid
        taskIdentifier = application.beginBackgroundTaskWithName(
            taskName = "Mapmory trip record upload",
            expirationHandler = {
                if (taskIdentifier != UIBackgroundTaskInvalid) {
                    application.endBackgroundTask(taskIdentifier)
                    taskIdentifier = UIBackgroundTaskInvalid
                }
            },
        )
        return try {
            block()
        } finally {
            if (taskIdentifier != UIBackgroundTaskInvalid) {
                application.endBackgroundTask(taskIdentifier)
            }
        }
    }
}
