/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.util.Date;

/**
 * Bean class for the exp.material table.
 * User: migra
 * Date: Jun 14, 2005
 */
public class Material extends RunItem
{
    private Integer materialSourceId;
    private Integer rootMaterialRowId;
    private String aliquotedFromLSID;
    private Integer sampleState;

    private Date materialExpDate;
    private Double storedAmount;
    private String units;

    // aliquot rollup columns
    private Integer aliquotCount;
    private Double aliquotVolume;
    private String aliquotUnit;

    public Material()
    {
        setCpasType(ExpMaterial.DEFAULT_CPAS_TYPE);
    }

    public Integer getMaterialSourceId()
    {
        return this.materialSourceId;
    }

    public void setMaterialSourceId(Integer materialSourceId)
    {
        this.materialSourceId = materialSourceId;
    }

    public Integer getRootMaterialRowId()
    {
        return rootMaterialRowId;
    }

    public void setRootMaterialRowId(Integer rootMaterialRowId)
    {
        this.rootMaterialRowId = rootMaterialRowId;
    }

    public String getAliquotedFromLSID()
    {
        return aliquotedFromLSID;
    }

    public void setAliquotedFromLSID(String aliquotedFromLSID)
    {
        this.aliquotedFromLSID = aliquotedFromLSID;
    }

    public Integer getSampleState()
    {
        return sampleState;
    }

    public Double getStoredAmount()
    {
        return storedAmount;
    }

    public void setStoredAmount(Double storedAmount)
    {
        this.storedAmount = storedAmount;
    }

    public String getUnits()
    {
        return units;
    }

    public void setUnits(String units)
    {
        this.units = units;
    }

    public void setSampleState(Integer sampleState)
    {
        this.sampleState = sampleState;
    }

    public Integer getAliquotCount()
    {
        return aliquotCount;
    }

    public Double getAliquotVolume()
    {
        return aliquotVolume;
    }

    public String getAliquotUnit()
    {
        return aliquotUnit;
    }

    public void setAliquotCount(Integer aliquotCount)
    {
        this.aliquotCount = aliquotCount;
    }

    public void setAliquotVolume(Double aliquotVolume)
    {
        this.aliquotVolume = aliquotVolume;
    }

    public void setAliquotUnit(String aliquotUnit)
    {
        this.aliquotUnit = aliquotUnit;
    }

    public Date getMaterialExpDate()
    {
        return materialExpDate;
    }

    public void setMaterialExpDate(Date materialExpDate)
    {
        this.materialExpDate = materialExpDate;
    }

    @Override
    public ActionURL detailsURL()
    {
        return detailsURL(getContainer(), false);
    }

    public ActionURL detailsURL(Container container, boolean checkForOverride)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class, checkForOverride).getMaterialDetailsURL(container, getRowId());
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Material material = (Material) o;

        return !(getRowId() == 0 || getRowId() != material.getRowId());
    }

    @Override
    public int hashCode()
    {
        return getRowId();
    }

    @Override
    public @Nullable ExpMaterialImpl getExpObject()
    {
        return new ExpMaterialImpl(this);
    }
}
