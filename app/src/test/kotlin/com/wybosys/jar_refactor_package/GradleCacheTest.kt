package com.wybosys.jar_refactor_package

import org.junit.Test

class GradleCacheTest {

    @Test
    fun update() {
        GradleCache.STANDALONES = listOf(
            APPLIBS
        )
        GradleCache.update()
    }

}