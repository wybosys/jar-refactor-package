package com.wybosys.jar_refactor_package

import org.junit.Test

class RefactorTest {

    @Test
    fun refactor() {
        val from = "com.android.support.constraint:constraint-layout-solver:1.1.3"
        val location = GradleCache.findByImplementation(from)!!
        val tgt = Pwd().resolve("build/${location.fileName}")
        Refactor().apply {
            packages = mapOf("android.support" to "rpkg.android.support")
        }.process(location, tgt)
    }

}
