package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;

public class CspUtils
{
    public static void collectCspViolations(Document doc, String name, Collection<String> violations)
    {
        // Collect nonce-less script tags
        NodeList nl = doc.getElementsByTagName("script");

        for (int i = 0; i < nl.getLength(); i++)
        {
            NamedNodeMap attributes = nl.item(i).getAttributes();
            Node nonce = attributes.getNamedItem("nonce");
            if (null == nonce)
            {
                Node src = attributes.getNamedItem("src");
                if (null == src)
                    violations.add(name + ": non-src script tag without a nonce!");
            }
        }

        // Collect inline event handlers
        logInlineEvents(doc, name, violations);

        // Collect javascript: hrefs in <a> tags
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
                    violations.add(name + ": <a> tag with href=" + href);
                }
            }
        }

        // Collect javascript: actions in <form> tags
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
                    violations.add(name + ": <form> tag with action=" + action);
                }
            }
        }
    }

    private static void logInlineEvents(Node node, String name, Collection<String> violations)
    {
        NamedNodeMap attributes = node.getAttributes();
        if (null != attributes)
        {
            for (int i = 0; i < attributes.getLength(); i++)
            {
                String nodeName = attributes.item(i).getNodeName();
                if (nodeName.startsWith("on"))
                {
                    violations.add(name + ": " + nodeName);
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            logInlineEvents(children.item(i), name, violations);
        }
    }
}
