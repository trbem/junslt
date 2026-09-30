package com.jev.probe.capture

import org.junit.Assert.*
import org.junit.Test

class ForegroundPolicyTest {
    @Test fun keyboardAndOverlayDoNotReplaceUnderlyingApplication() {
        // Only application windows enter the policy, so IME/system/own overlay events contribute none.
        assertEquals("com.tencent.mm", ForegroundPolicy.resolve(listOf(AppWindow("com.tencent.mm", 2, false)), "keyboard"))
        assertEquals("com.tencent.mm", ForegroundPolicy.resolve(emptyList(), "com.tencent.mm"))
    }
    @Test fun actualApplicationSwitchAndSplitScreenUseFocus() {
        assertEquals("launcher", ForegroundPolicy.resolve(listOf(AppWindow("launcher", 2, true)), "com.tencent.mm"))
        assertEquals("com.tencent.mm", ForegroundPolicy.resolve(listOf(AppWindow("other", 9, false), AppWindow("com.tencent.mm", 2, true)), null))
        assertEquals("top", ForegroundPolicy.resolve(listOf(AppWindow("bottom", 1, false), AppWindow("top", 3, false)), null))
    }
}
