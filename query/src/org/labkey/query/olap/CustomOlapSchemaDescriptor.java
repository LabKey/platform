/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.view.ActionURL;
import org.labkey.query.controllers.OlapController;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: 8/16/14
 *
 * High-level representation of a custom olap definition while
 * the OlapDef represents a row in the query.OlapDef table.
 *
 * CONSIDER: Currently, the custom olap def must be associated with a module for {@link org.labkey.api.module.Module#getOlapSchemaInfo()} info.
 */
public class CustomOlapSchemaDescriptor extends OlapSchemaDescriptor
{
    final OlapDef _olapDef;

    public CustomOlapSchemaDescriptor(@NotNull OlapDef olapDef)
    {
        super(olapDef.getConfigId(), olapDef.lookupModule());
        _olapDef = olapDef;
    }

    @Override
    public Container getContainer()
    {
        return _olapDef.lookupContainer();
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    @Override
    public String getDefinition()
    {
        return _olapDef.getDefinition();
    }

    @Override
    protected InputStream getInputStream() throws IOException
    {
        return IOUtils.toInputStream(getDefinition());
    }

    public ActionURL urlEdit()
    {
        return new ActionURL(OlapController.EditDefinitionAction.class, getContainer()).addParameter("rowId", _olapDef.getRowId());
    }

    public ActionURL urlDelete()
    {
        return new ActionURL(OlapController.DeleteDefinitionAction.class, getContainer()).addParameter("rowId", _olapDef.getRowId());
    }
}
