package com.wybosys.jar_refactor_package

import javassist.ByteArrayClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.expr.*
import java.io.ByteArrayOutputStream
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
                    if (entrySrc.name.endsWith(".jar")) {
                        processInnerJar(jarSrc, entrySrc, out)
                    } else if (entrySrc.name.endsWith(".class")) {
                        classes.add(jarSrc, entrySrc)
                    } else {
                        processNormal(jarSrc, entrySrc, out)
                    }
                }
            }
        }

        processClasses(classes, out)
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

    class JarClasses {

        private val pool = ClassPool()
        private val classes = mutableMapOf<String, CtClass>()
        private val processeds = mutableMapOf<String, Boolean>()
        private val classpaths = mutableMapOf<String, ByteArrayClassPath>()

        init {
            pool.appendSystemPath()
        }

        fun add(jar: JarFile, entry: JarEntry) {
            val bytes = ReadBytes(jar, entry)
            val qname = entry.name.replace(".class", "").replace("/", ".")
            add(qname, bytes)
        }

        fun find(qname: String): CtClass? {
            return classes[qname]
        }

        fun all(): Map<String, CtClass> {
            return classes.toMap()
        }

        fun add(qname: String, bytes: ByteArray) {
            val cp = ByteArrayClassPath(qname, bytes)
            pool.appendClassPath(cp)
            classes[qname] = pool.get(qname)
            classpaths[qname] = cp
        }

        fun duplicateAs(srcQname: String, tgtQname: String) {
            val src = pool.getAndRename(srcQname, tgtQname)
            val cp = ByteArrayClassPath(tgtQname, src.toByteArray())
            pool.appendClassPath(cp)

            classes[tgtQname] = pool.get(tgtQname)
            classpaths[tgtQname] = cp
        }

        fun remove(qname: String) {
            classes.remove(qname)
            pool.removeClassPath(classpaths.remove(qname))
        }

        fun markProcessed(qname: String) {
            processeds[qname] = true
        }

        fun isProcessed(qname: String): Boolean {
            return processeds[qname] ?: false
        }

    }

    fun processClasses(classes: JarClasses, out: JarOutputStream) {
        val origin = classes.all()

        origin.forEach { (qname, clz) ->
            applyPackages(qname).apply {
                if (first) {
                    classes.duplicateAs(qname, second)
                }
            }
        }

        origin.forEach { (qname, clz) ->
            if (classes.isProcessed(qname)) {
                return@forEach
            }
            classes.markProcessed(qname)

            if (qname.endsWith("ConstraintWidgetContainer")) {
                println()
            }

            // 替换方法中包含的
            clz.declaredMethods.forEach { mth ->
                val sig = mth.genericSignature
                if (sig != null) {
                    applyPackagesToSignature(sig).apply {
                        if (first) {
                            mth.genericSignature = second
                        }
                    }
                }
            }

            // 处理成员变量
            clz.declaredFields.forEach { fld ->
                val sig = fld.genericSignature
                if (sig != null) {
                    applyPackagesToSignature(sig).apply {
                        if (first) {
                            fld.genericSignature = second
                        }
                    }
                } else {
                    applyPackages(fld.type.name).apply {
                        if (first) {
                            if (!fld.type.isFrozen) {
                                fld.type.name = second
                            }
                        }
                    }
                }
            }

            // 替换代码中包含的
            clz.instrument(object : ExprEditor() {
                override fun edit(e: NewExpr) {
                    println("new ${e.signature} ${e.className}")
                }

                override fun edit(a: NewArray) {
                    val sig = a.componentType.genericSignature
                    println("newarray ${a.componentType.genericSignature} ${a.componentType.name}")
                }

                override fun edit(i: Instanceof) {
                    if (i.type.name.endsWith("Guideline")) {
                        i.replace("${'$'}_ = $1 instanceof String;")
                    }
                }

                override fun edit(c: Cast) {
                    if (c.type.name.endsWith("Guideline")) {
                        //c.replace("${'$'}_ = (${'$'}w)(${'$'}r)$1;")
                        c.replace("${'$'}_ = (String)$1;")
                    }
                }

                override fun edit(f: FieldAccess) {
                    //println("field ${f.className} ${f.fileName} ${f.fieldName} ${f.signature}")
                }
            })

            // 处理依赖
            clz.refClasses.forEach { depQname ->
                applyPackages(depQname).apply {
                    if (first) {
                        clz.replaceClassName(depQname, second)
                    }
                }
            }

            // 输出修改后的
            val bytes = clz.toByteArray()
            val fname = applyPackages(qname).second.replace('.', '/') + ".class"
            out.putNextEntry(ZipEntry(fname))
            out.write(bytes)
        }
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
