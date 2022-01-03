package com.wybosys.jar_refactor_package

import org.junit.Test
import kotlin.io.path.Path

class RefactorTest {

    @Test
    fun refactor() {
        val from = "com.android.support.constraint:constraint-layout-solver:1.1.3"
        val location = GradleCache.findByImplementation(from)!!
        //val tgt = Pwd().resolve("build/rpkg-${location.fileName}")
        val tgt =
            Path("/Users/wangyb03/develop/lianxin/zhangxin/android/social_main/rpkg/rpkg-constraint-layout-solver-1.1.3.jar")
        Refactor().apply {
            packages = mapOf("android.support" to "rpkg.android.support")
        }.process(location, tgt)
    }

    @Test
    fun refactorAll() {
        listOf(
            "com.android.support.constraint:constraint-layout-solver:1.1.3",
            "com.android.support.constraint:constraint-layout:1.1.3",
            "com.android.support:animated-vector-drawable:26.1.0",
            "com.android.support:appcompat-v7:26.1.0",
            "com.android.support:cardview-v7:26.1.0",
            "com.android.support:design:26.1.0",
            "com.android.support:recyclerview-v7:26.1.0",
            "com.android.support:support-annotations:26.1.0",
            "com.android.support:support-compat:26.1.0",
            "com.android.support:support-core-ui:26.1.0",
            "com.android.support:support-core-utils:26.1.0",
            "com.android.support:support-dynamic-animation:26.1.0",
            "com.android.support:support-fragment:26.1.0",
            "com.android.support:support-media-compat:26.1.0",
            "com.android.support:support-v4:26.1.0",
            "com.android.support:support-v13:26.1.0",
            "androidx.legacy:legacy-support-v4:1.0.0",
            "androidx.legacy:legacy-support-v13:1.0.0",
            "com.android.support:support-vector-drawable:26.1.0",
            "com.android.support:transition:26.1.0",
            "androidx.annotation:annotation:1.0.0",
            "androidx.cardview:cardview:1.0.0",
            "me.everything:overscroll-decor-android:1.0.4"
        ).forEach { from ->
            val location = GradleCache.findByImplementation(from)
                ?: throw Exception("没有找到 $from")
            val tgt =
                Path("/Users/wangyb03/develop/lianxin/zhangxin/android/social_main/rpkg/rpkg-${location.fileName}")
            Refactor().apply {
                packages = mapOf(
                    "android.support" to "rpkg.android.support",
                    "androidx." to "rpkg.androidx."
                )
            }.process(location, tgt)
        }
    }

}