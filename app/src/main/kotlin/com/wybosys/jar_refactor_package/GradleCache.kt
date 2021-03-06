package com.wybosys.jar_refactor_package

import org.rocksdb.Options
import org.rocksdb.RocksDB
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.streams.toList


object GradleCache {

    private var STORAGE = Home().resolve(".gradle/caches/modules-2/files-2.1")
    private val DB by lazy {
        RocksDB.open(Options().apply {
            setCreateIfMissing(true)
        }, "${STORAGE}/jar-refactor-packages.cdb")
    }

    /**
     * 独立文件夹，用于扫描独立的库
     */
    var STANDALONES = listOf<Path>()

    fun update(directory: Path = STORAGE) {
        updateFiles(directory)
        STANDALONES.forEach {
            if (it.isDirectory()) {
                scan(it)
            }
        }
    }

    private val PAT_JAR = Regex(""".+\.[ja]ar$""")
    private val PAT_PACKAGE = Regex("""^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*$""")

    /**
     * 扫描独立类库
     */
    fun scan(directory: Path) {
        Files.list(directory).forEach { each ->
            if (each.isDirectory()) {
                scan(each)
                return@forEach
            }

            if (!PAT_JAR.matches(each.name)) {
                return@forEach
            }

            DB.put(each.name.toByteArray(), each.pathString.toByteArray())
        }
    }

    /**
     * 更新file的列表
     */
    fun updateFiles(directory: Path) {
        Files.list(directory).forEach { each ->
            if (!each.isDirectory()) {
                return@forEach
            }

            // 文件名满足package的规则
            if (!PAT_PACKAGE.matches(each.name)) {
                return@forEach
            }

            updateGroup(each)
        }
    }

    fun updateGroup(groupDirectory: Path) {
        Files.list(groupDirectory).forEach { each ->
            if (!each.isDirectory()) {
                return@forEach
            }

            updateArtifact(each)
        }
    }

    fun updateArtifact(artifactDirectory: Path) {
        Files.list(artifactDirectory).forEach { each ->
            if (!each.isDirectory()) {
                return@forEach
            }

            updateVersion(each)
        }
    }

    fun updateVersion(versionDirectory: Path) {
        // 子文件中查找名称匹配的文件
        val version = versionDirectory.name
        val artifact = versionDirectory.parent.name
        val group = versionDirectory.parent.parent.name
        val impl = "$group:$artifact:$version"

        val tgts = setOf(
            "$artifact-$version.aar", "$artifact-$version.jar"
        )
        val fnds = Files.find(versionDirectory, Int.MAX_VALUE, { path, attr ->
            tgts.contains(path.name)
        }).toList()

        if (fnds.isEmpty()) {
            println("没有找到 $impl")
            return
        }

        if (fnds.size > 1) {
            println("找到多个 ${fnds.joinToString(" ")}，请处理后重新update")
            return
        }

        val fnd = fnds.first()
        println("找到 $impl")

        // 保存数据
        DB.put(impl.toByteArray(), fnd.pathString.toByteArray())
    }

    /**
     * 使用 implementation 格式查找包的路径
     */
    fun findByImplementation(impl: String): Path? {
        try {
            val fnd = DB[impl.toByteArray()].decodeToString()
            return Path(fnd)
        } catch (e: Exception) {
            return null
        }
    }

}
