package com.wybosys.jar_refactor_package

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun NodeList.toList(): List<Node> {
    val ret = mutableListOf<Node>()
    for (i in 0 until length) {
        ret.add(item(i))
    }
    return ret
}

fun NodeList.walk(proc: (node: Node) -> Unit) {
    toList().forEach { node ->
        proc(node)
        node.childNodes.walk(proc)
    }
}

fun NodeList.findNamed(name: String): List<Node> {
    val ret = mutableListOf<Node>()
    for (i in 0 until length) {
        val node = item(i)
        if (node.nodeName == name) {
            ret.add(node)
        }
    }
    return ret.toList()
}

fun NodeList.findByAttribute(name: String, key: String): List<Node> {
    val ret = mutableListOf<Node>()
    for (i in 0 until length) {
        val node = item(i)
        if (node.nodeName == name && node.findAttribute(key) != null) {
            ret.add(node)
        }
    }
    return ret
}

fun NodeList.findByAttribute(name: String, key: String, value: String): List<Node> {
    val ret = mutableListOf<Node>()
    for (i in 0 until length) {
        val node = item(i)
        if (node.nodeName == name && node.findAttribute(key, value) != null) {
            ret.add(node)
        }
    }
    return ret
}

fun NodeList.queryByAttribute(name: String, key: String, value: String): List<Node> {
    val ret = mutableListOf<Node>()
    for (i in 0 until length) {
        val node = item(i)
        if (node.nodeName == name && node.findAttribute(key, value) != null) {
            ret.add(node)
        }
        ret.addAll(node.childNodes.queryByAttribute(name, key, value))
    }
    return ret
}

fun Node.findAttribute(name: String): Node? {
    if (!hasAttributes()) {
        return null
    }

    return attributes.getNamedItem(name)
}

fun Node.clearChildNodes() {
    childNodes.toList().forEach {
        removeChild(it)
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

fun Node.findAttribute(key: String, value: String): Node? {
    if (!hasAttributes()) {
        return null
    }

    val fnd = attributes.getNamedItem(key)
    return if (fnd?.nodeValue == value) {
        fnd
    } else {
        null
    }
}

fun Node.setAttribute(key: String, value: String): Node {
    val node = ownerDocument.createAttribute(key)
    node.nodeValue = value
    attributes.setNamedItem(node)
    return node
}

fun ParseXml(stm: InputStream): Document {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stm)
}

fun Document.toByteArray(): ByteArray {
    val bytes = ByteArrayOutputStream()
    TransformerFactory.newInstance().apply {
        setAttribute("indent-number", 4)
    }.newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
    }.transform(DOMSource(this), StreamResult(bytes))
    return bytes.toByteArray()
}
