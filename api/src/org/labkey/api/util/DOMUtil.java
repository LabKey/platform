/*
 * Copyright (c) 2009-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.util;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/*
* User: Dave
* Date: Jan 12, 2009
* Time: 11:55:03 AM
*/

/**
 * Various DOM utility functions that are not in Xerxes
 */
public class DOMUtil
{
    /**
     * Returns all text within the node, including text within CDATA child elements
     * @param node The node
     * @return All the text within the node
     */
    public static String getNodeText(Node node)
    {
        if(null == node)
            return null;

        NodeList children = node.getChildNodes();
        if(null == children || children.getLength() == 0)
            return node.getTextContent();

        StringBuilder text = new StringBuilder();
        for(int idx = 0; idx < children.getLength(); ++idx)
        {
            Node child = children.item(idx);
            if(child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
                text.append(child.getTextContent());
        }

        return text.toString();
    }

    /**
     * Returns the string value of the attribute identified by attrName, or
     * defaultValue if the attribute does not exist on the element.
     * @param elem The element node
     * @param attrName The name of the attribute
     * @param defaultValue A default value to return if the attribute doesn't exist
     * @return The attribute value or the default value
     */
    public static String getAttributeValue(Node elem, String attrName, String defaultValue)
    {
        NamedNodeMap attrs = elem.getAttributes();
        if(null != attrs)
        {
            Node attr = attrs.getNamedItem(attrName);
            if(null != attr)
                return attr.getTextContent();
        }
        return defaultValue;
    }

    /**
     * Returns the string value of the attribute identified by attrName, or
     * null if the attribute does not exist on the element.
     * @param elem The element node
     * @param attrName The name of the attribute
     * @return The attribute value or null
     */
    public static String getAttributeValue(Node elem, String attrName)
    {
        return getAttributeValue(elem, attrName, null);
    }

    /**
     * Returns the child nodes of the given node as an iterable list
     * @param node The parent node
     * @return An iterable list of the child nodes (may be empty but will not be null)
     */
    public static List<Node> getChildNodes(Node node)
    {
        return getChildNodes(node, null);
    }

    public static List<Node> getChildNodes(Node node, Short nodeType)
    {
        NodeList children = node.getChildNodes();
        List<Node> ret = new ArrayList<>(children.getLength());
        for(int idx = 0; idx < children.getLength(); ++idx)
        {
            Node child = children.item(idx);

            if (null != nodeType && child.getNodeType() == nodeType.shortValue())
                ret.add(children.item(idx));
        }
        return ret;
    }

    /**
     * Returns a List of Node objects matching a given name.
     * If the node has no children or no children matching the
     * name, an empty list will be returned.
     * @param node The parent node
     * @param name The name of child nodes to find
     * @return A list of child nodes (may be empty, but not null)
     */
    public static List<Node> getChildNodesWithName(Node node, String name)
    {
        NodeList children = node.getChildNodes();
        if(null == children || children.getLength() == 0)
            return Collections.emptyList();

        List<Node> ret = new ArrayList<>();
        for(int idx=0; idx < children.getLength(); ++idx)
        {
            Node child = children.item(idx);
            if(child.getNodeName().equalsIgnoreCase(name))
                ret.add(child);
        }
        return ret;
    }

    /**
     * Returns the first child of the given node that has the given name,
     * or null if none are found.
     * @param node The parent node
     * @param name The name of the child node to find
     * @return The first child with that name, or null if none are found
     */
    public static Node getFirstChildNodeWithName(Node node, String name)
    {
        List<Node> nodes = getChildNodesWithName(node, name);
        return nodes.size() > 0 ? nodes.get(0) : null;
    }

    /**
     * Returns the first child node of the given node that is an element
     * @param node The parent node
     * @return The first child node that is an element, or null if there are none
     */
    public static Node getFirstChildElement(Node node)
    {
        List<Node> nodes = getChildNodes(node, Node.ELEMENT_NODE);
        return nodes.size() > 0 ? nodes.get(0) : null;
    }
}