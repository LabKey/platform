/*
 * Copyright (c) 2005-2007 Fred Hutchinson Cancer Research Center
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
package org.labkey.experiment.api;

import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.api.ExpProtocolApplication;

import java.util.Date;
import java.util.List;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 2:49:46 PM
 */
public class Material extends IdentifiableBase implements ProtocolOutput
{
    private int rowId;
    private String cpasType = "Material";
    private Integer sourceApplicationId;
    private String sourceProtocolLSID;
    private Integer runId;
    private Date created;
    private String container;
    private ExpProtocolApplication sourceApp;
    private List<ExpProtocolApplication> successorAppList;
    private List<Integer> successorRunIdList;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getCpasType()
    {
        return cpasType;
    }

    public void setCpasType(String cpasType)
    {
        this.cpasType = cpasType;
    }

    public Integer getSourceApplicationId()
    {
        return sourceApplicationId;
    }

    public void setSourceApplicationId(Integer sourceApplicationId)
    {
        this.sourceApplicationId = sourceApplicationId;
    }

    public String getSourceProtocolLSID()
    {
        return sourceProtocolLSID;
    }

    public void setSourceProtocolLSID(String sourceProtocolLSID)
    {
        this.sourceProtocolLSID = sourceProtocolLSID;
    }

    public Integer getRunId()
    {
        return runId;
    }

    public void setRunId(Integer runId)
    {
        this.runId = runId;
    }

    public Date getCreated()
    {
        return created;
    }

    public void setCreated(Date created)
    {
        this.created = created;
    }

    public void setContainer(String container)
    {
        this.container = container;
    }

    public String getContainer()
    {
        return container;
    }

    public ExpProtocolApplication retrieveSourceApp()
    {
        if (null == sourceApp)
            throw new IllegalStateException("sourceApp not populated");
        return sourceApp;
    }

    public void storeSourceApp(ExpProtocolApplication sourceApp)
    {
        this.sourceApp = sourceApp;
    }

    public List<ExpProtocolApplication> retrieveSuccessorAppList()
    {
        if (null == successorAppList)
            throw new IllegalStateException("successorAppList not populated");
        return successorAppList;
    }

    public void storeSuccessorAppList(List<ExpProtocolApplication> successorAppList)
    {
        this.successorAppList = successorAppList;
    }

    public List<Integer> retrieveSuccessorRunIdList()
    {
        if (null == successorRunIdList)
            throw new IllegalStateException("successorRunIdList not populated");
        return successorRunIdList;
    }

    public void storeSuccessorRunIdList(List<Integer> successorRunIdList)
    {
        this.successorRunIdList = successorRunIdList;
    }
    
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Material material = (Material) o;

        return !(rowId == 0 || rowId != material.rowId);

    }

    public int hashCode()
    {
        return rowId;
    }
}
