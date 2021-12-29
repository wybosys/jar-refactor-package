package com.wybosys.jar_refactor_package

import org.rocksdb.Options
import org.rocksdb.RocksDB
import java.nio.file.Files
import java.nio.file.Path
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

    fun update(directory: Path = STORAGE) {
        updateFiles(directory)
    }

    private val PAT_PACKAGE = Regex("""^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*$""")

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
            "$artifact-$version.aar",
            "$artifact-$version.jar"
        )
        val fnds = Files.find(versionDirectory, Int.MAX_VALUE,
            { path, attr ->
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
    fun findByImplementation(impl: String): String? {
        try {
            return DB[impl.toByteArray()].decodeToString()
        } catch (e: Exception) {
            return null
        }
    }

}
