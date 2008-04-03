package org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;

public class GWTCohort implements IsSerializable
{
    private String name;
    private String description;
    private int count;

    public GWTCohort()
    {
    }

    public GWTCohort(String name, String description, int count)
    {
        this.name = name;
        this.description = description;
        this.count = count;
    }

//    public Element toElement(Document doc)
//    {
//        Element el = createTag(doc, "name", name, "count", new Integer(count));
//        XMLUtils.addTextTag(el, "description", description);
//        return el;
//    }
//
//    public GWTCohort(Element el)
//    {
//        name = el.getAttribute("name");
//        count = Integer.parseInt(el.getAttribute("count"));
//        description = XMLUtils.getTextTag(el, "description");
//    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public int getCount()
    {
        return count;
    }

    public void setCount(int count)
    {
        this.count = count;
    }
}
