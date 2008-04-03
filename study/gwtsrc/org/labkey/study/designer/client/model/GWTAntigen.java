package org.labkey.study.designer.client.model;

import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Document;
import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 11:27:07 AM
 * To change this template use File | Settings | File Templates.
 */
public class GWTAntigen implements IsSerializable
{
    private String name;
    private String gene;
    private String subtype;
    private String sequence;
    private String genBankId;

    public GWTAntigen()
    {
        
    }
    public GWTAntigen(String name, String gene, String subtype, String genBankId, String sequence)
    {
        this.name = name;
        this.gene = gene;
        this.subtype = subtype;
        this.genBankId = genBankId;
        this.sequence = sequence;
    }
    
    public String toString()
    {
        return name + ": " + gene + (null != subtype ? ("(" + subtype + ")") : "");
    }

//    public Element toElement(Document doc)
//    {
//        Element el = createTag(doc, "name", name, "gene", gene, "subtype", subtype);
//        XMLUtils.addTextTag(el, "sequence", sequence);
//
//        return el;
//    }
//
//    public GWTAntigen(Element el)
//    {
//        this(el.getAttribute("name"), el.getAttribute("gene"), el.getAttribute("subtype"), XMLUtils.getTextTag(el, "sequence"));
//    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getGene()
    {
        return gene;
    }

    public void setGene(String gene)
    {
        this.gene = gene;
    }

    public String getSubtype()
    {
        return subtype;
    }

    public void setSubtype(String subtype)
    {
        this.subtype = subtype;
    }

    public String getSequence()
    {
        return sequence;
    }

    public void setSequence(String sequence)
    {
        this.sequence = sequence;
    }

    public String getGenBankId()
    {
        return genBankId;
    }

    public void setGenBankId(String genBankId)
    {
        this.genBankId = genBankId;
    }
}
