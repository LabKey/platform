package org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 11:31:18 AM
 * To change this template use File | Settings | File Templates.
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
