package com.wybosys.jar_refactor_package

import org.junit.Test
import kotlin.io.path.Path

val OUTPUT = Path("/Users/wangyb03/develop/lianxin/zhangxin/android/social_main/rpkg/")
val PACKAGES = mapOf(
    "android.support." to "rpkg.android.support.",
    "android.arch.lifecycle." to "rpkg.android.arch.lifecycle.",
    "android.databinding." to "rpkg.android.databinding.",
    "androidx." to "rpkg.androidx."
)
val PACKAGES_IGNORE = setOf(
    "android.support.annotation",
    "androidx.annotation"
)
val ANDROID = AndroidProfile().apply {
    compileSdkVersion = 28
}

class RefactorTest {

    @Test
    fun refactor() {
        val from = "android.arch.lifecycle:livedata:1.1.0"
        val location = GradleCache.findByImplementation(from)!!
        val tgt = OUTPUT.resolve("rpkg-${PackageName(from).replace('.', '_')}-${location.fileName}")
        Refactor().apply {
            packages = PACKAGES
            ignorePackages = PACKAGES_IGNORE
            android = ANDROID
        }.process(from, location, tgt)
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
            //"com.android.support:support-annotations:26.1.0",
            "com.android.support:support-compat:26.1.0",
            "com.android.support:support-core-ui:26.1.0",
            "com.android.support:support-core-utils:26.1.0",
            "com.android.support:support-media-compat:26.1.0",
            "com.android.support:support-dynamic-animation:26.1.0",
            "com.android.support:support-fragment:26.1.0",
            "com.android.support:support-v4:26.1.0",
            "com.android.support:support-v13:26.1.0",
            "com.android.support:multidex:1.0.0",
            "android.arch.lifecycle:common:1.1.0",
            "android.arch.lifecycle:extensions:1.1.0",
            "android.arch.lifecycle:viewmodel:1.1.0",
            "android.arch.lifecycle:runtime:1.1.0",
            "android.arch.lifecycle:livedata:1.1.0",
            "android.arch.lifecycle:livedata-core:1.1.0",
            "com.android.databinding:adapters:3.1.2",
            "com.android.databinding:baseLibrary:3.1.2",
            "com.android.databinding:library:3.1.2",
            "androidx.legacy:legacy-support-v4:1.0.0",
            "androidx.legacy:legacy-support-v13:1.0.0",
            "com.android.support:support-vector-drawable:26.1.0",
            "com.android.support:transition:26.1.0",
            //"androidx.annotation:annotation:1.0.0",
            "androidx.cardview:cardview:1.0.0",
            "me.everything:overscroll-decor-android:1.0.4",
            "com.zenmen.video:sdk:5.22.1.3",
            "com.zenmen.voicechat.sdk:lib-motion-sdk:1.0.1.0-SNAPSHOT",
            "com.zenmen.voicechat.sdk:lib-core:1.0.1.0-SNAPSHOT",
            "com.zenmen.voicechat.sdk:lib-common-motion:1.0.1.0-SNAPSHOT",
            "com.zenmen.video:goodgallery:1.3.7.5-SNAPSHOT",
            "com.zenmen.video:goodplayer:4.3.0.0-SNAPSHOT",
            "com.makeramen:roundedimageview:2.3.0",
            "com.github.bumptech.glide:glide:3.8.0",
            "com.scwang.smartrefresh:SmartRefreshLayout:1.1.2"
        ).forEach { from ->
            val location = GradleCache.findByImplementation(from)
                ?: throw Exception("没有找到 $from")
            val tgt = OUTPUT.resolve("rpkg-${PackageName(from).replace('.', '_')}-${location.fileName}")
            Refactor().apply {
                packages = PACKAGES
                ignorePackages = PACKAGES_IGNORE
                android = ANDROID
            }.process(from, location, tgt)
        }
    }

}
