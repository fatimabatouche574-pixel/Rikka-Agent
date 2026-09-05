package me.rerere.rikkahub.data.codexvl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexVLRootGuardTest {
    @Test
    fun `ordinary shell rejects root elevation bypasses`() {
        listOf(
            "su", "su -c id", "/system/xbin/su -c whoami", "echo ok && su -c id",
            "reboot", "setenforce 0", "magisk --install", "ls /data/data", "mount /system",
        ).forEach { assertFalse("Allowed critical command: $it", CodexVLRootGuard.ordinaryShellAllowed(it)) }
    }

    @Test
    fun `ordinary non root commands remain available`() {
        listOf("git status", "ls /sdcard/Download", "echo result", "cat README.md").forEach {
            assertTrue("Rejected safe command: $it", CodexVLRootGuard.ordinaryShellAllowed(it))
        }
    }
}
