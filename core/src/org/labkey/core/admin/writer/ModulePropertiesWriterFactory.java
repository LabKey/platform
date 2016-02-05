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
package org.labkey.core.admin.writer;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.ModulePropertiesType;
import org.labkey.folder.xml.ModulePropertyType;

import java.util.Map;

/**
 * User: vsharma
 * Date: 5/21/14
 * Time: 3:13 PM
 */
public class ModulePropertiesWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new ModulePropertiesWriter();
    }

    public class ModulePropertiesWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.CONTAINER_SPECIFIC_MODULE_PROPERTIES;
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();
            ModulePropertiesType modulePropsXml = folderXml.addNewModuleProperties();
            // Get the active modules in the container, and export any container specific module properties
            for(Module module: c.getActiveModules())
            {
                Map<String, ModuleProperty> propertyMap = module.getModuleProperties();
                if(propertyMap != null)
                {
                    for(Map.Entry<String, ModuleProperty> entry: propertyMap.entrySet())
                    {
                        ModuleProperty property = entry.getValue();
                        if(property.isCanSetPerContainer()) // Save only properties settable on containers
                        {
                            String value = property.getValueContainerSpecific(c);

                            if(!StringUtils.isBlank(value))
                            {
                                ModulePropertyType modulePropXml = modulePropsXml.addNewModuleProperty();
                                modulePropXml.setModuleName(module.getName());
                                modulePropXml.setPropertyName(property.getName());
                                modulePropXml.setValue(value);
                            }
                        }
                    }
                }
            }
        }

    }
}
