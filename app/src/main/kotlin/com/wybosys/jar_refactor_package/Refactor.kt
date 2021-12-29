package com.wybosys.jar_refactor_package

import javassist.ByteArrayClassPath
import javassist.ClassPool
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name

open class Refactor {

    /**
     * 需要重构的包名
     */
    var packages: Map<String, String> = mapOf()

    open fun process(from: Path, to: Path) {
        val jarSrc = JarFile(from.toFile())

        val tmp = to.parent.resolve("tmp." + to.name)
        val jarTmpOut = JarOutputStream(FileOutputStream(tmp.toFile().also {
            it.deleteOnExit()
        }))

        processJar(jarSrc, jarTmpOut)
        jarTmpOut.close()

        to.toFile().also {
            if (it.exists()) {
                it.delete()
            }
            tmp.toFile().renameTo(it)
        }
    }

    fun processJar(jarSrc: JarFile, out: JarOutputStream) {
        val iterSrc = jarSrc.entries()
        while (iterSrc.hasMoreElements()) {
            val entrySrc = iterSrc.nextElement()
            if (entrySrc.isDirectory) {
                continue
            }
            println("处理 ${entrySrc.name}")

            when (entrySrc.name) {
                "AndroidManifest.xml" -> {
                    processAndroidManifest(jarSrc, entrySrc, out)
                }
                else -> {
                    if (entrySrc.name.endsWith(".jar")) {
                        processInnerJar(jarSrc, entrySrc, out)
                    } else if (entrySrc.name.endsWith(".class")) {
                        processClass(jarSrc, entrySrc, out)
                    } else {
                        processNormal(jarSrc, entrySrc, out)
                    }
                }
            }
        }
    }

    fun processAndroidManifest(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        var content = ReadString(jar, entry)
        content = applyPackages(content).second

        out.putNextEntry(ZipEntry(entry.name))
        out.write(content.encodeToByteArray())
    }

    fun processNormal(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        out.putNextEntry(ZipEntry(entry.name))

        val bytes = ReadBytes(jar, entry)
        out.write(bytes)
    }

    fun processInnerJar(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        val innerFrom = Pwd().resolve("tmp.inner.from")
        innerFrom.toFile().writeBytes(ReadBytes(jar, entry))

        val innerTo = Pwd().resolve("tmp.inner.to")
        process(innerFrom, innerTo)

        out.putNextEntry(ZipEntry(entry.name))
        out.write(innerTo.toFile().readBytes())

        innerFrom.deleteIfExists()
        innerTo.deleteIfExists()
    }

    fun processClass(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        var bytes = ReadBytes(jar, entry)
        val qname = entry.name.replace(".class", "").replace("/", ".")

        val pool = ClassPool.getDefault()
        pool.insertClassPath(ByteArrayClassPath(qname, bytes))

        val clsSrc = pool.getCtClass(qname)
        clsSrc.refClasses.forEach { clz ->
            applyPackages(clz).apply {
                if (first) {
                    clsSrc.replaceClassName(clz, second)
                }
            }
        }
        clsSrc.classFile.apply {
            name = applyPackages(name).second

            val tmp = ByteArrayOutputStream()
            write(DataOutputStream(tmp))
            bytes = tmp.toByteArray()
        }

        val fname = applyPackages(qname).second.replace('.', '/') + ".class"
        out.putNextEntry(ZipEntry(fname))
        out.write(bytes)
    }

    protected fun applyPackages(content: String): Pair<Boolean, String> {
        var changed = false
        var ret = content
        packages.forEach { (old, new) ->
            if (ret.startsWith(old)) {
                ret = ret.replace(old, new)
                changed = true
            }
        }
        return Pair(changed, ret)
    }

    protected companion object {

        fun ReadString(jar: JarFile, entry: JarEntry): String {
            val oriStm = jar.getInputStream(entry)
            var bytes: Int
            val buffer = ByteArray(1024)

            var ret = ""
            while (oriStm.read(buffer).also { bytes = it } != -1) {
                ret += buffer.decodeToString(0, bytes)
            }
            return ret
        }

        fun ReadBytes(jar: JarFile, entry: JarEntry): ByteArray {
            val oriStm = jar.getInputStream(entry)
            var bytes: Int
            val buffer = ByteArray(1024)

            val ret = ByteArrayOutputStream()
            while (oriStm.read(buffer).also { bytes = it } != -1) {
                ret.write(buffer, 0, bytes)
            }
            return ret.toByteArray()
        }

    }
}
