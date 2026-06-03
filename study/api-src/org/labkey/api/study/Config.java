/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.study;

import org.labkey.api.data.ConvertHelper;

public class Config
{
    // detect whether the filter might not have been created via the cohort picker
    private boolean ambiguous = false;

    private CohortFilter.Type type;
    private Integer cohortId;
    private String label;
    private Boolean enrolled;

    public CohortFilter.Type getType()
    {
        return type;
    }

    public void setType(String s)
    {
        CohortFilter.Type type = null;
        try
        {
            type = CohortFilter.Type.valueOf(s);
        }
        catch (Exception x)
        {/* */}
        setType(type);
    }

    public void setType(CohortFilter.Type type)
    {
        if (null == type)
            return;
        if (null != this.type)
            this.ambiguous = true;
        this.type = type;
    }

    public Integer getCohortId()
    {
        return cohortId;
    }

    public void setCohortId(String s)
    {
        Integer i = null;
        try
        {
            i = Integer.parseInt(s);
        }
        catch (Exception x)
        {/* */}
        setCohortId(i);
    }

    public void setCohortId(Integer cohortId)
    {
        if (null == cohortId)
            return;
        if (null != this.label || null != this.cohortId)
            this.ambiguous = true;
        this.cohortId = cohortId;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        if (null == label)
            return;
        if (null != this.label || null != this.cohortId)
            this.ambiguous = true;
        this.label = label;
    }

    public Boolean getEnrolled()
    {
        return enrolled;
    }

    public void setEnrolled(String s)
    {
        Boolean b = null;
        try
        {
            b = ConvertHelper.convert(s, Boolean.class);
        }
        catch (Exception x)
        {/* */}
        setEnrolled(b);
    }

    public void setEnrolled(Boolean enrolled)
    {
        if (null == enrolled)
            return;
        if (null != this.enrolled)
            ambiguous = true;
        this.enrolled = enrolled;
    }

    public boolean getAmbiguous()
    {
        return ambiguous;
    }

    public void setAmbiguous(boolean ambiguous)
    {
        this.ambiguous = ambiguous;
    }
}
