/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.query.reports;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.ExportDirType;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:39:43 PM
 */
public class ReportWriter extends BaseFolderWriter
{
    private static final String DEFAULT_DIRECTORY = "reports";

    public String getSelectionText()
    {
        return "Reports";
    }

    @Override
    public boolean includeInType(AbstractFolderContext.ExportType type)
    {
        return AbstractFolderContext.ExportType.ALL == type || AbstractFolderContext.ExportType.STUDY == type;
    }    

    @Override
    public void write(Container object, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        Report[] reports = ReportService.get().getReports(ctx.getUser(), ctx.getContainer());

        if (reports.length > 0)
        {
            ExportDirType reportsXml = ctx.getXml().addNewReports();
            reportsXml.setDir(DEFAULT_DIRECTORY);
            VirtualFile reportsDir = vf.getDir(DEFAULT_DIRECTORY);

            for (Report report : reports)
            {
                report.serializeToFolder(ctx, reportsDir);
            }
        }
    }
    
    public static class Factory implements FolderWriterFactory
    {
        public FolderWriter create()
        {
            return new ReportWriter();
        }
    }
}
