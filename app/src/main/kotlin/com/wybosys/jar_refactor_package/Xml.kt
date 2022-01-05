package com.wybosys.jar_refactor_package

import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun NodeList.walk(proc: (node: Node) -> Unit) {
    for (i in 0 until length) {
        val node = item(i)
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

fun NamedNodeMap.forEach(proc: (node: Node) -> Unit) {
    for (i in 0 until length) {
        val node = item(i)
        proc(node)
    }
}

fun Node.findAttribute(name: String): Node? {
    if (!hasAttributes()) {
        return null
    }

    return attributes.getNamedItem(name)
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
