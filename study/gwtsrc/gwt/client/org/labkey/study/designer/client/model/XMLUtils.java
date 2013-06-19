/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client.model;

import com.google.gwt.core.client.GWT;
import com.google.gwt.xml.client.*;

/**
 * User: Mark Igra
 * Date: Jan 31, 2007
 * Time: 2:28:51 PM
 */
public class XMLUtils
{
    public interface ClassNameService
    {
        public String getClassName(Object obj);
    }

    private static class GWTClassNameService implements ClassNameService
    {
        public String getClassName(Object obj)
        {
            return GWT.getTypeName(obj);
        }
    }

    private static ClassNameService service = new GWTClassNameService();
    public static String tagName(Object obj)
    {
        String typeName = GWT.getTypeName(obj);
        int index = typeName.lastIndexOf('.');
        if (index >= 0)
            typeName = typeName.substring(index + 1);

        return typeName;
    }

    public static String pluralTagName(Object obj)
    {
        String tagName = tagName(obj);
        //UNDONE: Custom pluralizing...
        return tagName + "s";
    }

    public static void setClassNameService(ClassNameService s)
    {
        service = s;
    }

    public static Element safeSetAttrs(Element el, String[] attrNames, Object[] attrVals)
    {
        assert attrNames.length == attrVals.length;
        for (int i = 0; i < attrNames.length; i++)
            safeSetAttr(el, attrNames[i], attrVals[i]);

        return el;
    }

    public static Element safeSetAttr(Element el, String attrName, Object attrVal)
    {
        if (null != attrVal)
            el.setAttribute(attrName, attrVal.toString());
        return el;
    }

    public static void addTextTag(Element el, String tagName, String text)
    {
        if (null != text)
        {
            Element elChild = el.getOwnerDocument().createElement(tagName);
            elChild.appendChild(el.getOwnerDocument().createTextNode(text));
        }
    }

    public static String getTextTag(Element el, String tagName)
    {
        Element elText = getChildElement(el, tagName);
        if (null == elText)
            return null;

        return elText.getNodeValue();
    }

    public static Element getChildElement(Element parent, String childName)
    {
        NodeList nl = parent.getElementsByTagName(childName);
        if (null == nl || nl.getLength() == 0)
            return null;

        if (nl.getLength() > 1)
            throw new IllegalArgumentException("Expected only one child element with tag " + childName);

        return (Element) nl.item(0);
    }
}
