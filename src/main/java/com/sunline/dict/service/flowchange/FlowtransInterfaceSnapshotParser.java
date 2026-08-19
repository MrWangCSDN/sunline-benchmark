package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldSnapshot;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public class FlowtransInterfaceSnapshotParser {

    private final DocumentBuilderSupplier documentBuilderSupplier;

    public FlowtransInterfaceSnapshotParser() {
        this(FlowtransInterfaceSnapshotParser::newDocumentBuilder);
    }

    FlowtransInterfaceSnapshotParser(DocumentBuilderSupplier documentBuilderSupplier) {
        this.documentBuilderSupplier = Objects.requireNonNull(
                documentBuilderSupplier, "documentBuilderSupplier");
    }

    public FlowtransInterfaceSnapshot parse(String xmlContent) {
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = documentBuilderSupplier.get();
        } catch (ParserConfigurationException configurationFailure) {
            throw new IllegalStateException("Unable to configure secure XML parser", configurationFailure);
        }

        Document document;
        try {
            document = documentBuilder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (SAXException malformedContent) {
            throw new DeterministicContentException(
                    "Unable to parse flowtrans interface snapshot", malformedContent);
        } catch (IOException readFailure) {
            throw new IllegalStateException("Unable to read flowtrans interface snapshot", readFailure);
        }

        Element interfaceElement = findInterface(document.getDocumentElement());
        if (interfaceElement == null) {
            throw new DeterministicContentException("Missing interface element");
        }

        Map<FieldIdentity, FieldSnapshot> fields = new LinkedHashMap<>();
        walkIoTrees(interfaceElement, fields);
        return new FlowtransInterfaceSnapshot(
                interfaceElement.getAttribute("id"),
                interfaceElement.getAttribute("longname"),
                fields);
    }

    private static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        return builder;
    }

    private Element findInterface(Element root) {
        if ("interface".equals(root.getTagName())) {
            return root;
        }
        NodeList interfaces = root.getElementsByTagName("interface");
        return interfaces.getLength() == 0 ? null : (Element) interfaces.item(0);
    }

    private void walkIoTrees(Element interfaceElement, Map<FieldIdentity, FieldSnapshot> fields) {
        NodeList children = interfaceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String ioType = element.getTagName();
            if ("input".equals(ioType) || "output".equals(ioType)) {
                walk(element, ioType, "/", fields);
            }
        }
    }

    private void walk(Element parent, String ioType, String fieldPath,
                      Map<FieldIdentity, FieldSnapshot> fields) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if ("field".equals(element.getTagName())) {
                addField(element, ioType, fieldPath, fields);
                walk(element, ioType, fieldPath, fields);
            } else if ("fields".equals(element.getTagName())) {
                walk(element, ioType, appendContainer(fieldPath, element), fields);
            } else {
                walk(element, ioType, fieldPath, fields);
            }
        }
    }

    private void addField(Element element, String ioType, String fieldPath,
                          Map<FieldIdentity, FieldSnapshot> fields) {
        String fieldId = element.getAttribute("id");
        if (fieldId.isBlank()) {
            throw new DeterministicContentException("Field id must be nonblank");
        }

        FieldIdentity identity = new FieldIdentity(ioType, fieldPath, fieldId);
        FieldSnapshot snapshot = new FieldSnapshot(identity, attributes(element));
        if (fields.putIfAbsent(identity, snapshot) != null) {
            throw new DeterministicContentException("Duplicate field identity: " + identity);
        }
    }

    private String appendContainer(String fieldPath, Element container) {
        String id = container.getAttribute("id");
        String segment = id.isBlank() ? "fields[#" + attributeDigest(container) + "]"
                : "fields[" + id + "]";
        return "/".equals(fieldPath) ? "/" + segment : fieldPath + "/" + segment;
    }

    private SortedMap<String, String> attributes(Element element) {
        NamedNodeMap attributeNodes = element.getAttributes();
        SortedMap<String, String> attributes = new TreeMap<>();
        for (int i = 0; i < attributeNodes.getLength(); i++) {
            Node attribute = attributeNodes.item(i);
            attributes.put(attribute.getNodeName(), attribute.getNodeValue());
        }
        return attributes;
    }

    private String attributeDigest(Element container) {
        List<Map.Entry<String, String>> attributes = new ArrayList<>(attributes(container).entrySet());
        attributes.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
        StringBuilder canonicalAttributes = new StringBuilder();
        for (Map.Entry<String, String> attribute : attributes) {
            canonicalAttributes.append(attribute.getKey()).append('\u0000')
                    .append(attribute.getValue()).append('\u0000');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalAttributes.toString().getBytes(StandardCharsets.UTF_8));
            return toHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    @FunctionalInterface
    interface DocumentBuilderSupplier {
        DocumentBuilder get() throws ParserConfigurationException;
    }

    /** A repeatable failure caused only by the supplied Flowtrans XML content. */
    public static final class DeterministicContentException extends IllegalArgumentException {

        public DeterministicContentException(String message) {
            super(message);
        }

        public DeterministicContentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
