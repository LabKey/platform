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

import org.labkey.api.admin.AbstractFolderContext.ExportType;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.MvUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.MissingValueIndicatorsType;
import org.labkey.folder.xml.MissingValueIndicatorsType.MissingValueIndicator;

import java.util.Map;

/**
 * User: cnathe
 * Date: May 1, 2012
 */
public class MissingValueWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new MissingValueWriter();
    }

    public static class MissingValueWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.MISSING_VALUE_INDICATORS;
        }

        @Override
        public boolean selectedByDefault(ExportType type, boolean forTemplate)
        {
            return ExportType.ALL == type || ExportType.STUDY == type;
        }

        @Override
        public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
        {
            // Export to the new "folder"-namespace "missingValueIndicator" element
            MissingValueIndicatorsType mvXml = ctx.getXml().addNewMissingValueIndicator();
            Map<String, String> mvMap = MvUtil.getIndicatorsAndLabels(c);
            for (Map.Entry<String, String> mv : mvMap.entrySet())
            {
                MissingValueIndicator indXml = mvXml.addNewMissingValueIndicator();
                indXml.setIndicator(mv.getKey());
                indXml.setLabel(mv.getValue());
            }            
        }
    }
}
