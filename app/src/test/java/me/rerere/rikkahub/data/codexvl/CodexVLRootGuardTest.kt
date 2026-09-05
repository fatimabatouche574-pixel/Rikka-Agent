package me.rerere.rikkahub.data.codexvl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexVLRootGuardTest {
    @Test
    fun `ordinary shell rejects root elevation bypasses`() {
        listOf(
            "su", "su -c id", "/system/xbin/su -c whoami", "echo ok && su -c id",
            "'su' -c id", "s\\u -c id", "reboot", "setenforce 0", "magisk --install",
            "ls /data/data", "ls /data/user/0", "ls /data/adb", "mount /system",
        ).forEach { assertFalse("Allowed critical command: $it", CodexVLRootGuard.ordinaryShellAllowed(it)) }
    }

    @Test
    fun `executable form cannot bypass the root guard`() {
        assertFalse(
            CodexVLRootGuard.ordinaryShellAllowed(
                command = null,
                executable = "/system/xbin/su",
                arguments = listOf("-c", "id"),
            )
        )
        assertFalse(
            CodexVLRootGuard.ordinaryShellAllowed(
                command = null,
                executable = "/data/data/com.termux/files/usr/bin/bash",
                arguments = listOf("-c", "'su' -c id"),
            )
        )
        assertTrue(
            CodexVLRootGuard.ordinaryShellAllowed(
                command = null,
                executable = "/data/data/com.termux/files/usr/bin/git",
                arguments = listOf("status"),
            )
        )
    }

    @Test
    fun `root shell policy is always ask and never always allow`() {
        assertTrue(me.rerere.rikkahub.data.ai.tools.ToolApprovalDefaults.requiresApproval("android.root_shell"))
        assertFalse(me.rerere.rikkahub.data.ai.tools.ToolApprovalDefaults.allowsAlwaysAllow("android.root_shell"))
    }

    @Test
    fun `ordinary non root commands remain available`() {
        listOf("git status", "ls /sdcard/Download", "echo result", "cat README.md").forEach {
            assertTrue("Rejected safe command: $it", CodexVLRootGuard.ordinaryShellAllowed(it))
        }
    }
}
