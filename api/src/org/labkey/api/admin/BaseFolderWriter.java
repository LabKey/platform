/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.admin;

import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.folder.xml.FolderDocument;

import java.util.Collection;

/**
 * User: cnathe
 * Date: Apr 13, 2012
 */
public class BaseFolderWriter implements FolderWriter
{
    @Override
    public boolean show(Container c)
    {
        return true;
    }

    @Override
    public boolean selectedByDefault(AbstractFolderContext.ExportType type)
    {
        return AbstractFolderContext.ExportType.ALL == type;
    }

    @Override
    public Collection<Writer> getChildren(boolean sort, boolean forTemplate)
    {
        return null;
    }

    @Override
    public String getDataType()
    {
        return null;
    }

    @Override
    public void write(Container object, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {}

    @Override
    public boolean includeWithTemplate()
    {
        return true;
    }

    @Override
    public void initialize(ImportContext<FolderDocument.Folder> context)
    {
        // Do nothing
    }
}
