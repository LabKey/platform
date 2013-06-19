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

import org.labkey.api.gwt.client.util.StringUtils;

import java.util.List;
import java.util.ArrayList;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 11:27:56 AM
 */
public class GWTImmunogen extends VaccineComponent implements IsSerializable
{
    private String type;
    private String admin;
    private List<GWTAntigen> antigens = new ArrayList<GWTAntigen>();
    
    public GWTImmunogen()
    {
        
    }
    public GWTImmunogen(String name, String dose, String type, String admin)
    {
        this.setName(name);
        this.setDose(dose);
        this.type = type;
        this.admin = admin;
    }

    public boolean equals(Object o)
    {
        GWTImmunogen i = (GWTImmunogen) o;
        return super.equals(o) && StringUtils.equals(type, i.type) && StringUtils.equals(admin, i.admin);
    }

    public int hashCode()
    {
        return (getName() + getDose() + type + admin + antigenString()).hashCode();
    }

    private String antigenString()
    {
        String sep = "";
        StringBuffer sb = new StringBuffer();
        for (GWTAntigen antigen : antigens)
        {
            sb.append(sep);
            sb.append(antigen.toString());
            sep = ",";
        }
        return sb.toString();
    }

//    public Element toElement(Document doc)
//    {
//        Element el = createTag(doc, "name", name, "dose", dose, "type", type, "admin", admin);
//        Element elAntigens = doc.createElement(new GWTAntigen().pluralTagName());
//        for (int i = 0; i < antigens.size(); i++)
//        {
//            Element elAntigen = ((GWTAntigen) antigens.get(i)).toElement(doc);
//            elAntigens.appendChild(elAntigen);
//        }
//        el.appendChild(elAntigens);
//
//        return el;
//    }
//
//    public Element toRefElement(Document doc)
//    {
//        return createRefTag(doc, name);
//    }
//
//    public static GWTImmunogen fromRefElement(Element ref, List immunogens)
//    {
//        String name = ref.getAttribute("name");
//        for (int i = 0; i < immunogens.size(); i++)
//        {
//            GWTImmunogen immunogen = (GWTImmunogen) immunogens.get(i);
//            if (immunogen.name.equals(name))
//                return immunogen;
//        }
//
//        return null;
//    }
//
//    public GWTImmunogen(Element el)
//    {
//        name = el.getAttribute("name");
//        dose = el.getAttribute("dose");
//        type = el.getAttribute("type");
//        admin = el.getAttribute("admin");
//        Element elAntigens = XMLUtils.getChildElement(el, new GWTAntigen().pluralTagName());
//        NodeList nl = elAntigens.getElementsByTagName(new GWTAntigen().tagName());
//        for (int i = 0; i < nl.getLength(); i++)
//        {
//            Element elAntigen = (Element) nl.item(i);
//            antigens.add(new GWTAntigen(elAntigen));
//        }
//    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getAdmin()
    {
        return admin;
    }

    public void setAdmin(String admin)
    {
        this.admin = admin;
    }

    public List<GWTAntigen> getAntigens()
    {
        return antigens;
    }

    public void setAntigens(List<GWTAntigen> antigens)
    {
        this.antigens = antigens;
    }
}
