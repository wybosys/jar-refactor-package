package com.wybosys.jar_refactor_package


import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name
import kotlin.io.path.pathString

class AndroidProfile {
    var compileSdkVersion: Int = 0
}

open class Refactor {

    /**
     * 需要重构的包名
     */
    var packages: Map<String, String> = mapOf()

    /**
     * android特殊重构配置
     */
    var android: AndroidProfile? = null

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
        val classes = JarClasses()

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
                    val path = entrySrc.name
                    if (path.endsWith(".jar")) {
                        processInnerJar(jarSrc, entrySrc, out)
                    } else if (path.endsWith(".class")) {
                        classes.add(jarSrc, entrySrc)
                    } else {
                        if (android != null) {
                            if (path.startsWith("res")) {
                                if (path.startsWith("res/layout")) {
                                    processAndroidLayout(jarSrc, entrySrc, out)
                                    continue
                                }
                            }
                        }
                        if (path.startsWith("META-INF") and path.endsWith(".version")) {
                            processVersion(jarSrc, entrySrc, out)
                            continue
                        }
                        processNormal(jarSrc, entrySrc, out)
                    }
                }
            }
        }

        processClasses(classes, out)
    }

    fun processAndroidManifest(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        val doc = ParseXml(jar.getInputStream(entry))
        doc.childNodes.findNamed("manifest").first().apply {
            findAttribute("package").also {
                if (it != null) {
                    applyPackagesToQName(it.nodeValue).apply {
                        if (first) {
                            it.nodeValue = second
                        }
                    }
                }
            }
        }

        out.putNextEntry(ZipEntry(entry.name))
        out.write(doc.toByteArray())
    }

    fun processNormal(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        out.putNextEntry(ZipEntry(entry.name))

        val bytes = ReadBytes(jar, entry)
        out.write(bytes)
    }

    fun processVersion(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        var path = Path(entry.name)
        path = path.resolveSibling("rpkg_${path.name}")
        out.putNextEntry(ZipEntry(path.pathString))

        val bytes = ReadBytes(jar, entry)
        out.write(bytes)
    }

    fun processAndroidLayout(jar: JarFile, entry: JarEntry, out: JarOutputStream) {
        val doc = ParseXml(jar.getInputStream(entry))

        doc.childNodes.walk { node ->
            if (node.hasAttributes()) {
                if (android!!.compileSdkVersion == 28) {
                    node.removeAttribute("app:civ_circle_background_color")
                }
            }
        }

        val bytes = doc.toByteArray()
        out.putNextEntry(ZipEntry(entry.name))
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

    class JarClasses {

        private val classes = mutableMapOf<String, ByteArray>()
        private val processeds = mutableMapOf<String, Boolean>()

        fun add(jar: JarFile, entry: JarEntry) {
            val bytes = ReadBytes(jar, entry)
            val qname = entry.name.replace(".class", "").replace("/", ".")
            add(qname, bytes)
        }

        fun all(): Map<String, ByteArray> {
            return classes.toMap()
        }

        fun add(qname: String, bytes: ByteArray) {
            classes[qname] = bytes
        }

        fun markProcessed(qname: String) {
            processeds[qname] = true
        }

        fun isProcessed(qname: String): Boolean {
            return processeds[qname] ?: false
        }

    }

    class RpkgMapper(val refactor: Refactor) : Remapper() {

        override fun map(internalName: String): String {
            val name = refactor.applyPackagesToClass(internalName).let {
                if (it.first) {
                    it.second
                } else {
                    internalName
                }
            }
            return super.map(name)
        }

    }

    fun processClasses(classes: JarClasses, out: JarOutputStream) {
        val origin = classes.all()

        origin.forEach { (qname, clz) ->
            if (classes.isProcessed(qname)) {
                return@forEach
            }
            classes.markProcessed(qname)

            val reader = ClassReader(clz)
            val writer = ClassWriter(0)
            val mapper = RpkgMapper(this)
            reader.accept(ClassRemapper(writer, mapper), 0)

            // 输出修改后的
            val bytes = writer.toByteArray()
            val fname = applyPackagesToQName(qname).second.replace('.', '/') + ".class"
            out.putNextEntry(ZipEntry(fname))
            out.write(bytes)
        }
    }

    protected fun applyPackagesToContent(content: String): Pair<Boolean, String> {
        var changed = false
        var ret = content
        packages.forEach { (old, new) ->
            if (ret.contains(old)) {
                ret = ret.replace(old, new)
                changed = true
            }
        }
        return Pair(changed, ret)
    }

    protected fun applyPackagesToQName(qname: String): Pair<Boolean, String> {
        var changed = false
        var ret = qname
        packages.forEach { (old, new) ->
            if (ret.startsWith(old)) {
                ret = ret.replace(old, new)
                changed = true
            }
        }
        return Pair(changed, ret)
    }

    protected fun applyPackagesToSignature(sig: String): Pair<Boolean, String> {
        var changed = false
        var ret = sig
        packages.forEach { (old, new) ->
            val sigold = "L${old.replace('.', '/')}"
            val signew = "L${new.replace('.', '/')}"

            if (ret.contains(sigold)) {
                ret = ret.replace(sigold, signew)
                changed = true
            }
        }
        return Pair(changed, ret)
    }

    protected fun applyPackagesToClass(sig: String): Pair<Boolean, String> {
        var changed = false
        var ret = sig
        packages.forEach { (old, new) ->
            val sigold = old.replace('.', '/')
            val signew = new.replace('.', '/')

            if (ret.contains(sigold)) {
                ret = ret.replace(sigold, signew)
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
