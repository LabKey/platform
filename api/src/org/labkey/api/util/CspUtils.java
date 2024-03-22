package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.function.Consumer;

public class CspUtils
{
    public static void enumerateCspViolations(Document doc, Consumer<String> consumer)
    {
        // Enumerate nonce-less script tags
        NodeList nl = doc.getElementsByTagName("script");

        for (int i = 0; i < nl.getLength(); i++)
        {
            NamedNodeMap attributes = nl.item(i).getAttributes();
            Node nonce = attributes.getNamedItem("nonce");
            if (null == nonce)
            {
                Node src = attributes.getNamedItem("src");
                if (null == src)
                    consumer.accept("non-src script tag without a nonce");
            }
        }

        // Enumerate inline event handlers
        enumerateInlineEvents(doc, consumer);

        // Enumerate javascript: hrefs in <a> tags
        nl = doc.getElementsByTagName("a");

        for (int i = 0; i < nl.getLength(); i++)
        {
            NamedNodeMap attributes = nl.item(i).getAttributes();
            Node hrefNode = attributes.getNamedItem("href");
            if (null != hrefNode)
            {
                String href = hrefNode.getTextContent();
                if (StringUtils.startsWithIgnoreCase(href, "javascript:"))
                {
                    consumer.accept("<a> tag with href=" + href);
                }
            }
        }

        // Enumerate javascript: actions in <form> tags
        nl = doc.getElementsByTagName("form");

        for (int i = 0; i < nl.getLength(); i++)
        {
            NamedNodeMap attributes = nl.item(i).getAttributes();
            Node actionNode = attributes.getNamedItem("action");
            if (null != actionNode)
            {
                String action = actionNode.getTextContent();
                if (StringUtils.startsWithIgnoreCase(action, "javascript:"))
                {
                    consumer.accept("<form> tag with action=" + action);
                }
            }
        }
    }

    private static void enumerateInlineEvents(Node node, Consumer<String> consumer)
    {
        NamedNodeMap attributes = node.getAttributes();
        if (null != attributes)
        {
            for (int i = 0; i < attributes.getLength(); i++)
            {
                String nodeName = attributes.item(i).getNodeName();
                if (nodeName.startsWith("on"))
                {
                    consumer.accept(nodeName);
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            enumerateInlineEvents(children.item(i), consumer);
        }
    }
}
