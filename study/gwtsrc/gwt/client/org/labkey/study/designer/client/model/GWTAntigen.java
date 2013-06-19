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
 * Time: 11:27:07 AM
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
