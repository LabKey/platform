/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.Arrays;

/**
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
        //Make sure default lab is valid
        if (labs != null && labs.length > 0)
        {
            if (this.defaultLab == null || !Arrays.asList(labs).contains(this.defaultLab))
                this.defaultLab = labs[0];
        }
        else
            this.defaultLab = null;
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
