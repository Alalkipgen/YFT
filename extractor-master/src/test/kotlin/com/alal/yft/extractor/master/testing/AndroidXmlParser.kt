package com.alal.yft.extractor.master.testing

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Android's own `DocumentBuilderFactory` (`org.apache.harmony.xml.parsers`) as it answers: no
 * XInclude setting, no feature but namespaces and validation, no attribute (as F1's test in
 * `core-media`). The documents themselves come from the JVM's parser, with no hardening of its
 * own, so a document type reaches it.
 */
class AndroidLikeDocumentBuilderFactory : DocumentBuilderFactory() {
    override fun newDocumentBuilder(): DocumentBuilder =
        newInstance(JVM_FACTORY, AndroidLikeDocumentBuilderFactory::class.java.classLoader)
            .also { it.isNamespaceAware = isNamespaceAware }
            .newDocumentBuilder()

    override fun setXIncludeAware(state: Boolean): Unit =
        throw UnsupportedOperationException("This parser does not support specification")

    override fun setAttribute(name: String, value: Any?): Unit = throw IllegalArgumentException(name)

    override fun getAttribute(name: String): Any = throw IllegalArgumentException(name)

    override fun setFeature(name: String, value: Boolean) {
        if (name != NAMESPACES && name != VALIDATION) throw ParserConfigurationException(name)
    }

    override fun getFeature(name: String): Boolean = when (name) {
        NAMESPACES -> isNamespaceAware
        VALIDATION -> isValidating
        else -> throw ParserConfigurationException(name)
    }

    private companion object {
        const val JVM_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl"
        const val NAMESPACES = "http://xml.org/sax/features/namespaces"
        const val VALIDATION = "http://xml.org/sax/features/validation"
    }
}

/** Runs [block] with [AndroidLikeDocumentBuilderFactory] as the JVM's factory. */
inline fun <T> withAndroidParser(block: () -> T): T {
    val property = "javax.xml.parsers.DocumentBuilderFactory"
    val before = System.getProperty(property)
    System.setProperty(property, AndroidLikeDocumentBuilderFactory::class.java.name)
    try {
        return block()
    } finally {
        if (before == null) System.clearProperty(property) else System.setProperty(property, before)
    }
}
