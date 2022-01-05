package com.wybosys.jar_refactor_package

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList

fun NodeList.walk(proc: (node: Node) -> Unit) {
    for (i in 0 until length) {
        val node = item(i)
        proc(node)

        node.childNodes.walk(proc)
    }
}

fun NamedNodeMap.forEach(proc: (node: Node) -> Unit) {
    for (i in 0 until length) {
        val node = item(i)
        proc(node)
    }
}

fun Node.removeAttribute(name: String): Node? {
    if (!hasAttributes()) {
        return null
    }

    val fnd = attributes.getNamedItem(name)
    if (fnd != null) {
        attributes.removeNamedItem(name)
    }

    return fnd
}