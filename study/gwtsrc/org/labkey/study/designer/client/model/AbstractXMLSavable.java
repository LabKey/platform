package org.labkey.study.designer.client.model;

import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jan 31, 2007
 * Time: 2:47:37 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractXMLSavable implements XMLSavable
{
    public String tagName()
    {
        return XMLUtils.tagName(this);
    }

    public String refTagName()
    {
        return tagName() + "Ref";
    }

    public String pluralTagName()
    {
        return XMLUtils.pluralTagName(this);
    }

    protected Element createTag(Document doc)
    {
        return doc.createElement(tagName());
    }

    protected Element createTag(Document doc, String attr1, Object attr1Val)
    {
        Element el = createTag(doc);
        return XMLUtils.safeSetAttr(el, attr1, attr1Val);
    }

    protected Element createTag(Document doc, String attr1, Object attr1Val, String attr2, Object attr2Val)
    {
        Element el = createTag(doc);
        return XMLUtils.safeSetAttrs(el, new String[] {attr1, attr2}, new Object[] {attr1Val, attr2Val});
    }

    protected Element createTag(Document doc, String attr1, Object attr1Val, String attr2, Object attr2Val, String attr3, Object attr3Val)
    {
        Element el = createTag(doc);
        return XMLUtils.safeSetAttrs(el, new String[] {attr1, attr2, attr3}, new Object[] {attr1Val, attr2Val, attr3Val});
    }

    protected Element createTag(Document doc, String attr1, Object attr1Val, String attr2, Object attr2Val, String attr3, Object attr3Val, String attr4, Object attr4val)
    {
        Element el = createTag(doc);
        return XMLUtils.safeSetAttrs(el, new String[] {attr1, attr2, attr3, attr4}, new Object[] {attr1Val, attr2Val, attr3Val, attr4val});
    }

    protected Element createRefTag(Document doc, String name)
    {
        Element el = doc.createElement(tagName() + "Ref");
        el.setAttribute("name", name);
        return el;
    }


}
