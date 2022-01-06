package com.wybosys.jar_refactor_package


import java.nio.file.Path
import kotlin.io.path.Path

fun Home(): Path {
    return Path(System.getProperty("user.home"))
}

fun Pwd(): Path {
    return Path(System.getProperty("user.dir"))
}

fun PackageName(qname: String): String {
    return qname.split(":").first()
}