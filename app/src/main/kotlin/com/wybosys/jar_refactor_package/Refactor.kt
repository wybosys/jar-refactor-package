package com.wybosys.jar_refactor_package

import javassist.ByteArrayClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import javassist.expr.MethodCall
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
        private val paths = mutableMapOf<String, ByteArrayClassPath>()

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

        fun forEach(action: (Map.Entry<String, CtClass>) -> Unit) {
            classes.toMap().forEach(action)
        }

        fun rename(oldQname: String, newQname: String, newBytes: ByteArray) {
            val oldCp = paths.remove(oldQname)!!
            pool.removeClassPath(oldCp)

            val cp = ByteArrayClassPath(newQname, newBytes)
            pool.appendClassPath(cp)
            paths[newQname] = cp

            classes.remove(oldQname)!!.apply {
                detach()
            }
            classes[newQname] = pool.getCtClass(newQname)
        }

        fun add(qname: String, bytes: ByteArray) {
            val cp = ByteArrayClassPath(qname, bytes)
            pool.appendClassPath(cp)
            classes[qname] = pool.getCtClass(qname)
            paths[qname] = cp
        }

        fun markProcessed(qname: String) {
            processeds[qname] = true
        }

        fun isProcessed(qname: String): Boolean {
            return processeds[qname] ?: false
        }

    }

    fun processClass(qname: String, clazz: CtClass, classes: JarClasses, out: JarOutputStream) {
        if (classes.isProcessed(qname)) {
            return
        }

        classes.markProcessed(qname)

        if (qname.endsWith("ConstraintWidgetContainer")) {
            println()
        }

        // 替换方法中包含的
        clazz.methods.forEach { mth ->
            val sig = mth.genericSignature
            if (sig != null) {
                applyPackagesToSignature(sig).apply {
                    if (first) {
                        mth.genericSignature = second
                    }
                }
            }
        }

        // 替换属性
        clazz.fields.forEach { fld ->
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
                        } else {
                            println()
                        }
                    }
                }
            }
        }

        // 处理依赖
        clazz.refClasses.forEach { depQname ->
            classes.find(depQname).also { depClz ->
                if (depClz == null) {
                    return@forEach
                }

                processClass(depQname, depClz, classes, out)

                // 改依赖的名称
                applyPackages(depQname).apply {
                    if (first) {
                        clazz.replaceClassName(depQname, second)
                    }
                }
            }
        }

        // 处理代码段
        clazz.instrument(object : ExprEditor() {
            override fun edit(m: MethodCall) {
                super.edit(m)
                try {
                    println("clazz edit methodcall ${m.signature}")
                    if (m.methodName == "iterator") {
                        println()
                    }
                } catch (e: Exception) {
                    println("${clazz} ${m} ${classes}")
                }
            }

            override fun edit(f: FieldAccess) {
                super.edit(f)
                println("clazz edit fieldaccess ${f.fieldName} ${f.signature}}")
            }
        })

        // 将改过的类添加到池中
        applyPackages(qname).apply {
            if (first) {
                val bytes: ByteArray
                clazz.classFile.apply {
                    val tmp = ByteArrayOutputStream()
                    write(DataOutputStream(tmp))
                    bytes = tmp.toByteArray()
                }

                classes.add(second, bytes)
            }
        }
    }

    fun processClasses(classes: JarClasses, out: JarOutputStream) {
        classes.forEach { (qname, clz) ->
            processClass(qname, clz, classes, out)

            // 输出修改后的
            val bytes: ByteArray
            clz.classFile.apply {
                val tmp = ByteArrayOutputStream()
                write(DataOutputStream(tmp))
                bytes = tmp.toByteArray()
            }

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
