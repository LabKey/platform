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
 * Time: 1:20:02 PM
 */
public class GWTAssayDefinition implements IsSerializable
{
    private String assayName;
    private String lab;

    public GWTAssayDefinition()
    {

    }

    public GWTAssayDefinition(GWTAssayDefinition copyFrom)
    {
        this.assayName = copyFrom.assayName;
        this.lab = copyFrom.lab;
    }

    public GWTAssayDefinition(String assayName, String lab)
    {
        this.assayName = assayName;
        this.lab = lab;
    }

    public String toString()
    {
        return assayName;
    }
    
    public boolean equals(Object o)
    {
        if (null == o)
            return false;

        GWTAssayDefinition ad = (GWTAssayDefinition) o;
        return (assayName == null ? ad.assayName == null : assayName.equals(ad.assayName)) &&
                (lab == null ? ad.lab == null : lab.equals(ad.lab));
    }

    public int hashCode()
    {
        return (assayName == null ? 0 : assayName.hashCode()) ^ (lab == null ? 1 : lab.hashCode());
    }

    public String getAssayName()
    {
        return assayName;
    }

    public void setAssayName(String assayName)
    {
        this.assayName = assayName;
    }

    public String getLab()
    {
        return lab;
    }

    public void setLab(String lab)
    {
        this.lab = lab;
    }
}
