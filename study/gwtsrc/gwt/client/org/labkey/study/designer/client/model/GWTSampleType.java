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

import java.util.List;

/**
* User: Mark Igra
* Date: Nov 8, 2007
* Time: 3:05:59 PM
*/
public class GWTSampleType implements IsSerializable
{
    public GWTSampleType()
    {

    }

    private String name;
    private String primaryType;
    private String code;

    public GWTSampleType(String name, String primaryType, String code)
    {
        this.name = name;
        this.primaryType = primaryType;
        this.code = code;
    }

    public String toString()
    {
        return name;
    }

    /**
     * Short code suitable for use in sample ids
     * @return
     */
    public String getShortCode()
    {
        return code;
    }

    public int hashCode()
    {
        return name.hashCode();
    }

    public boolean equals(Object x)
    {
        return null == x ? false : ((GWTSampleType) x).name.equals(name);
    }

    public static final GWTSampleType PLASMA = new GWTSampleType("Plasma", "Blood", "P");
    public static final GWTSampleType SERUM = new GWTSampleType("Serum", "Blood",  "S");
    public static final GWTSampleType PBMC = new GWTSampleType("PBMC", "Blood", "C");
    public static final GWTSampleType VAGINAL_MUCOSAL = new GWTSampleType("Vaginal Mucosal", "Vaginal Mucosal", "V");
    public static final GWTSampleType NASAL_MUCOSAL = new GWTSampleType("Nasal Mucosal", "Nasal Mucosal", "N");
    public static final GWTSampleType[] DEFAULTS =new GWTSampleType[]{PLASMA, SERUM, PBMC, VAGINAL_MUCOSAL, NASAL_MUCOSAL};

    public static GWTSampleType fromString(String str, GWTStudyDefinition studyDef)
    {
        List/*<GWTSampleType>*/ list = studyDef.getSampleTypes();
        for (int i = 0; i < list.size(); i++)
        {
            GWTSampleType sampleType = (GWTSampleType) list.get(i);
            if (sampleType.name.equalsIgnoreCase(str))
                return sampleType;
        }

        return null;
    }

    public String getPrimaryType()
    {
        return primaryType;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setPrimaryType(String primaryType)
    {
        this.primaryType = primaryType;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }
}
