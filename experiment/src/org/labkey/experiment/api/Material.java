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

/**
 * Bean class for the exp.material table.
 * User: migra
 * Date: Jun 14, 2005
 */
public class Material extends RunItem
{
    private String rootMaterialLSID;
    private String aliquotedFromLSID;
    private Integer sampleState;

    public Material()
    {
        setCpasType(ExpMaterial.DEFAULT_CPAS_TYPE);
    }

    public String getRootMaterialLSID()
    {
        return rootMaterialLSID;
    }

    public void setRootMaterialLSID(String rootMaterialLSID)
    {
        this.rootMaterialLSID = rootMaterialLSID;
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

    public void setSampleState(Integer sampleState)
    {
        this.sampleState = sampleState;
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
