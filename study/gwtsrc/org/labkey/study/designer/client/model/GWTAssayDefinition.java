package org.labkey.study.designer.client.model;

import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;
import com.google.gwt.xml.client.NodeList;
import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 1:20:02 PM
 */
public class GWTAssayDefinition implements IsSerializable
{
    private String name;
    private String description;
    private String[] labs;
    private String defaultLab;
    private GWTSampleMeasure defaultMeasure;
    private boolean locked;

    public GWTAssayDefinition()
    {

    }

    public GWTAssayDefinition(GWTAssayDefinition copyFrom)
    {
        this.name = copyFrom.name;
        this.labs = copyFrom.labs;
        this.defaultLab = copyFrom.defaultLab;
        this.description = copyFrom.description;
        this.defaultMeasure = new GWTSampleMeasure(copyFrom.defaultMeasure);
    }

    public GWTAssayDefinition(String name, String[] labs, GWTSampleMeasure defaultMeasure)
    {
        this.name = name;
        this.labs = labs;
        if (null != labs && labs.length > 0)
            this.defaultLab = labs[0];
        this.defaultMeasure = defaultMeasure;
    }

    public String toString()
    {
        return name;
    }
    
    public boolean equals(Object o)
    {
        if (null == o)
            return false;

        GWTAssayDefinition ad = (GWTAssayDefinition) o;
        return (name == null ? ad.name == null : name.equals(ad.name)) &&
                (labs == null ? ad.labs == null : labs.equals(ad.labs)) &&
                (defaultLab == null ? ad.defaultLab == null : defaultLab.equals(ad.defaultLab));
    }

    public int hashCode()
    {
        return (name == null ? 0 : name.hashCode()) ^ (labs == null ? 1 : labs.hashCode()) ^ (defaultLab == null ? 2 : defaultLab.hashCode());
    }

//    public Element toElement(Document doc)
//    {
//        Element el = createTag(doc, "name", name, "defaultLab", defaultLab);
//        Element elLabs = doc.createElement("Labs");
//        for (int l = 0; l < labs.length; l++)
//        {
//            Element elLab = doc.createElement("Lab");
//            elLab.setAttribute("name", labs[l]);
//            elLabs.appendChild(elLab);
//        }
//        el.appendChild(elLabs);
//        el.appendChild(defaultMeasure.toElement(doc));
//        XMLUtils.addTextTag(el, "description", description);
//
//        return el;
//    }
//
//    public GWTAssayDefinition(Element el)
//    {
//        name = el.getAttribute("name");
//        defaultLab = el.getAttribute("defaultLab");
//        Element elLabs = XMLUtils.getChildElement(el, "Labs");
//        NodeList nl = elLabs.getElementsByTagName("Lab");
//        ArrayList/*<String>*/ labsList = new ArrayList/*<String>*/();
//        labs = new String[nl.getLength()];
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element elLab = (Element) nl.item(i);
//            labs[i] = elLab.getAttribute("name");
//        }
//        defaultMeasure = new GWTSampleMeasure(XMLUtils.getChildElement(el, new GWTSampleMeasure().tagName()));
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

    public String[] getLabs()
    {
        return labs;
    }

    public void setLabs(String[] labs)
    {
        this.labs = labs;
    }

    public String getDefaultLab()
    {
        return defaultLab;
    }

    public void setDefaultLab(String defaultLab)
    {
        this.defaultLab = defaultLab;
    }

    public GWTSampleMeasure getDefaultMeasure()
    {
        return defaultMeasure;
    }

    public void setDefaultMeasure(GWTSampleMeasure defaultMeasure)
    {
        this.defaultMeasure = defaultMeasure;
    }

    public void setLocked(boolean locked)
    {
        this.locked = locked;
    }

    public boolean isLocked()
    {
        return locked;
    }
}
