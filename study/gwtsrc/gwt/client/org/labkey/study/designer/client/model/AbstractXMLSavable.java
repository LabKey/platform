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

import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;

/**
 * User: Mark Igra
 * Date: Jan 31, 2007
 * Time: 2:47:37 PM
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
