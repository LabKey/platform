/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.assay.plate.WellGroupImpl;

/**
 * Used to serialize to the WellGroup table
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WellGroupBean
{
    private Integer _rowId;
    private String _lsid;
    private Long _plateId;
    private String _name;
    private Boolean _template;
    private String _typename;

    public static WellGroupBean from(WellGroupImpl wellGroup)
    {
        WellGroupBean bean = new WellGroupBean();

        bean.setRowId(wellGroup.getRowId());
        bean.setLsid(wellGroup.getLSID());
        bean.setPlateId(wellGroup.getPlateId());
        bean.setName(wellGroup.getName());
        bean.setTemplate(wellGroup.isTemplate());
        bean.setTypename(wellGroup.getTypeName());

        return bean;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Long getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(Long plateId)
    {
        _plateId = plateId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Boolean getTemplate()
    {
        return _template;
    }

    public void setTemplate(Boolean template)
    {
        _template = template;
    }

    public String getTypename()
    {
        return _typename;
    }

    public void setTypename(String typename)
    {
        _typename = typename;
    }
}
