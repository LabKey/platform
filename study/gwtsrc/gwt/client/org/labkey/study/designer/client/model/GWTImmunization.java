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

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.List;import java.util.ArrayList;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 11:45:57 AM
 */
public class GWTImmunization implements IsSerializable
{
    private int hashCode = 0;
    public List<GWTImmunogen> immunogens = new ArrayList<GWTImmunogen>();
    public List<GWTAdjuvant> adjuvants = new ArrayList<GWTAdjuvant>();

    public GWTImmunization()
    {
        
    }

    public boolean equals(Object o)
    {
        if (null == o)
            return false;

        GWTImmunization i = (GWTImmunization) o;
        return i.immunogens.equals(immunogens) && i.adjuvants.equals(adjuvants);
    }

    public int hashCode()
    {
        if (0 == hashCode)
            hashCode = (toString().hashCode());

        return hashCode;
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        String sep = "";
        for (GWTImmunogen immunogen : immunogens)
        {
            sb.append(sep);
            sb.append(immunogen.getName());
            sep = "|";
        }
        int i = 0;
        while (i < adjuvants.size())
        {
            sb.append(sep);
            sb.append(adjuvants.get(i).getName());
            sep = "|";
            i++;
        }

        return sb.length() > 0 ? sb.toString() : "(none)";
    }

//    public Element toElement(Document doc)
//    {
//        Element el = createTag(doc);
//        for (int i = 0; i < immunogens.size(); i++)
//            el.appendChild(((GWTImmunogen) immunogens.get(i)).toRefElement(doc));
//        for (int i = 0; i < adjuvants.size(); i++)
//            el.appendChild(((GWTAdjuvant) adjuvants.get(i)).toRefElement(doc));
//
//        return el;
//    }
//
//    public GWTImmunization(Element el, GWTStudyDefinition definition)
//    {
//        NodeList nl = el.getElementsByTagName(new GWTImmunogen().refTagName());
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element immunogenElement = (Element) nl.item(i);
//            immunogens.add(GWTImmunogen.fromRefElement(immunogenElement, definition.immunogens));
//        }
//        nl = el.getElementsByTagName(new GWTAdjuvant().refTagName());
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element adjuvantElement = (Element) nl.item(i);
//            adjuvants.add(GWTAdjuvant.fromRefElement(adjuvantElement, definition.adjuvants));
//        }
//    }
//    public Element toElement(Document doc)
//    {
//        Element el = createTag(doc);
//        for (int i = 0; i < immunogens.size(); i++)
//            el.appendChild(((GWTImmunogen) immunogens.get(i)).toRefElement(doc));
//        for (int i = 0; i < adjuvants.size(); i++)
//            el.appendChild(((GWTAdjuvant) adjuvants.get(i)).toRefElement(doc));
//
//        return el;
//    }
//
//    public GWTImmunization(Element el, GWTStudyDefinition definition)
//    {
//        NodeList nl = el.getElementsByTagName(new GWTImmunogen().refTagName());
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element immunogenElement = (Element) nl.item(i);
//            immunogens.add(GWTImmunogen.fromRefElement(immunogenElement, definition.immunogens));
//        }
//        nl = el.getElementsByTagName(new GWTAdjuvant().refTagName());
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element adjuvantElement = (Element) nl.item(i);
//            adjuvants.add(GWTAdjuvant.fromRefElement(adjuvantElement, definition.adjuvants));
//        }
//    }
}