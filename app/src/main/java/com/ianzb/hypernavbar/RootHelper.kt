package com.ianzb.hypernavbar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

object RootHelper {

    var isRootAvailable: Boolean = false
        private set

    suspend fun checkRoot(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val stream = DataOutputStream(process.outputStream)
                stream.writeBytes("echo rootok\n")
                stream.flush()
                stream.writeBytes("exit\n")
                stream.flush()
                process.waitFor()
                val exitCode = process.exitValue()
                val result = exitCode == 0
                isRootAvailable = result
                process.destroy()
                result
            } catch (_: Exception) {
                isRootAvailable = false
                false
            }
        }
    }
}
