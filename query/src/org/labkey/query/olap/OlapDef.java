/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.query.olap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Entity;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.NotFoundException;
import org.labkey.query.persist.QueryManager;

/**
 * User: kevink
 * Date: 8/18/14
 *
 * Represents a row in the {@link QueryManager#getTableInfoQueryDef()} table.
 * @see org.labkey.query.olap.CustomOlapSchemaDescriptor
 */
public class OlapDef extends Entity
{
    private int _rowId;
    private String _name;
    private String _module;
    private String _description;
    private String _definition;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getModule()
    {
        return _module;
    }

    public void setModule(String module)
    {
        _module = module;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getDefinition()
    {
        return _definition;
    }

    public void setDefinition(String definition)
    {
        _definition = definition;
    }

    @NotNull
    public final Module lookupModule()
    {
        Module m = ModuleLoader.getInstance().getModule(_module);
        if (m == null)
            throw new NotFoundException("Module '" + _module + "' not found");
        return m;
    }

    public String getConfigId()
    {
        return OlapSchemaCacheHandler.createOlapCacheKey(lookupModule(), getName());
    }
}
