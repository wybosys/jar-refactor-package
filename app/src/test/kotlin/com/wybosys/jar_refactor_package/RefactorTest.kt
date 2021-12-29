package com.wybosys.jar_refactor_package

import org.junit.Test
import kotlin.io.path.Path

class RefactorTest {

    @Test
    fun refactor() {
        val from = "com.android.support.constraint:constraint-layout:1.1.3"
        val location = GradleCache.findByImplementation(from)
        val tgt = Pwd().resolve("build/constraint-layout-1.1.3.aar")
        Refactor().process(Path(location!!), tgt)
    }

}
