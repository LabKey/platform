/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: matthew
 * Date: 10/30/13
 */
public class ModuleOlapSchemaDescriptor extends OlapSchemaDescriptor
{
    final Resource _resource;

    public ModuleOlapSchemaDescriptor(@NotNull String id, @NotNull Module module, @NotNull Resource resource)
    {
        super(id, module);
        _resource = resource;
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public String getDefinition()
    {
        try
        {
            return PageFlowUtil.getStreamContentsAsString(_resource.getInputStream());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected InputStream getInputStream() throws IOException
    {
        return _resource.getInputStream();
    }
}

