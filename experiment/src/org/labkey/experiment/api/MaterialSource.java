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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

/**
 * Bean class for the exp.materialsource table. Referred to as sample types within the UI.
 * User: migra
 * Date: Aug 15, 2005
 */
public class MaterialSource extends IdentifiableEntity implements Comparable<MaterialSource>
{
    private String materialLSIDPrefix;
    private String description;
    /** PropertyURI */
    private String _idCol1;
    /** PropertyURI */
    private String _idCol2;
    /** PropertyURI */
    private String _idCol3;
    /** PropertyURI */
    private String _parentCol;

    private String _nameExpression;
    private String _labelColor;

    private String _materialParentImportAliasMap;

    public String getMaterialLSIDPrefix()
    {
        return materialLSIDPrefix;
    }

    public void setMaterialLSIDPrefix(String materialLSIDPrefix)
    {
        this.materialLSIDPrefix = materialLSIDPrefix;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    /** @return PropertyURI or 'Name' for first id column */
    public String getIdCol1()
    {
        return _idCol1;
    }

    /** @param idCol1 PropertyURI for first id column */
    public void setIdCol1(String idCol1)
    {
        _idCol1 = idCol1;
    }

    /** @return PropertyURI for second id column */
    public String getIdCol2()
    {
        return _idCol2;
    }

    /** @param idCol2 PropertyURI for second id column */
    public void setIdCol2(String idCol2)
    {
        _idCol2 = idCol2;
    }

    /** @return PropertyURI for third id column */
    public String getIdCol3()
    {
        return _idCol3;
    }

    /** @param idCol3 PropertyURI for third id column */
    public void setIdCol3(String idCol3)
    {
        _idCol3 = idCol3;
    }

    /** @return PropertyURI for column that points at parent materials */
    public String getParentCol()
    {
        return _parentCol;
    }

    /** @param parentCol PropertyURI for column that points at parent materials */
    public void setParentCol(String parentCol)
    {
        _parentCol = parentCol;
    }

    public String getNameExpression()
    {
        return _nameExpression;
    }

    public void setNameExpression(String nameExpression)
    {
        _nameExpression = nameExpression;
    }

    public String getLabelColor()
    {
        return _labelColor;
    }

    public void setLabelColor(String labelColor)
    {
        _labelColor = labelColor;
    }

    public String getMaterialParentImportAliasMap()
    {
        return _materialParentImportAliasMap;
    }

    public void setMaterialParentImportAliasMap(String materialParentImportAliasMap)
    {
        _materialParentImportAliasMap = materialParentImportAliasMap;
    }

    @Override
    public @Nullable ActionURL detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowSampleTypeAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.SampleSets.name(), FieldKey.fromParts(ExpSampleTypeTable.Column.RowId.name()), getRowId());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof MaterialSource)) return false;
        MaterialSource ms = (MaterialSource) o;
        return !(getRowId() == 0 || getRowId() != ms.getRowId());
    }

    @Override
    public int hashCode()
    {
        return getRowId();
    }

    @Override
    public int compareTo(@NotNull MaterialSource o)
    {
        return getName().compareToIgnoreCase(o.getName());
    }

    @Override
    public @Nullable ExpSampleTypeImpl getExpObject()
    {
        return new ExpSampleTypeImpl(this);
    }
}
