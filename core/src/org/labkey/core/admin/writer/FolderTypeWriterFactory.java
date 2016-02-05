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
package org.labkey.core.admin.writer;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.FolderType;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class FolderTypeWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new FolderTypeWriter();
    }

    public class FolderTypeWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.FOLDER_TYPE_AND_ACTIVE_MODULES;
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();
            FolderType ftXml = folderXml.addNewFolderType();
            ftXml.setName(c.getFolderType().getName());

            if (null != c.getDefaultModule(ctx.getUser()))
            {
                ftXml.setDefaultModule(c.getDefaultModule(ctx.getUser()).getName());
            }             

            FolderType.Modules modulesXml = ftXml.addNewModules();
            for (Module module : c.getActiveModules())
            {
                modulesXml.addModuleName(module.getName());
            }
        }

    }
}
