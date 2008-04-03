package org.labkey.study.designer.client.model;

import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jan 30, 2007
 * Time: 12:15:51 AM
 * To change this template use File | Settings | File Templates.
 */
public interface XMLSavable
{
    public Element toElement(Document doc);
    public String tagName();
    public String pluralTagName();
}
