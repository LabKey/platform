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
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

/**
 * Bean class for the exp.material table.
 * User: migra
 * Date: Jun 14, 2005
 */
public class Material extends RunItem
{
    private String rootMaterialLSID;
    private String aliquotedFromLSID;
    private Integer status;

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

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    @Override
    public ActionURL detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
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
