package com.wybosys.jar_refactor_package

import java.nio.file.Path

open class Refactor {

    open fun process(from: Path, to: Path) {
        if (from.endsWith(".aar")) {
            processAar(from, to)
        } else {
            processJar(from, to)
        }
    }

    fun processAar(from: Path, to: Path) {
        
    }

    fun processJar(from: Path, to: Path) {

    }

}
