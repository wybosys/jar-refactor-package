package com.wybosys.jar_refactor_package

import javassist.CtClass
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

fun CtClass.toByteArray(): ByteArray {
    val bytes: ByteArray
    classFile.apply {
        val tmp = ByteArrayOutputStream()
        write(DataOutputStream(tmp))
        bytes = tmp.toByteArray()
    }
    return bytes
}
