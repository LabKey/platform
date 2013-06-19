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

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 11:31:18 AM
 */
public class GWTAdjuvant extends VaccineComponent implements IsSerializable
{
    public String admin;
    public GWTAdjuvant(String name, String dose, String admin)
    {
        this.setName(name);
        this.setDose(dose);
        this.admin = admin;
    }
    public GWTAdjuvant()
    {
        
    }

//    public Element toElement(Document doc)
//    {
//        return createTag(doc, "name", name, "dose", dose, "admin", admin);
//    }
//
//    public Element toRefElement(Document doc)
//    {
//        Element el = createRefTag(doc, name);
//        return el;
//    }
//
//    public static GWTAdjuvant fromRefElement(Element el, List/*<Adjuvant>*/ adjuvants)
//    {
//        String name = el.getAttribute("name");
//        for (int i = 0; i < adjuvants.size(); i++)
//        {
//            GWTAdjuvant adjuvant = (GWTAdjuvant) adjuvants.get(i);
//            if (adjuvant.name.equals(name))
//                return adjuvant;
//        }
//        return null;
//    }
//
//    public GWTAdjuvant(Element el)
//    {
//        name = el.getAttribute("name");
//        dose = el.getAttribute("dose");
//        admin = el.getAttribute("admin");
//    }
}
