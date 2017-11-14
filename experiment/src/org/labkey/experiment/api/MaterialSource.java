/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

/**
 * Bean class for the exp.materialsource table. Referred to as sample sets within the UI.
 * User: migra
 * Date: Aug 15, 2005
 */
public class MaterialSource extends IdentifiableEntity
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
}
