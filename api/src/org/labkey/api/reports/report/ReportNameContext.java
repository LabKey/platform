/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
package org.labkey.api.reports.report;


import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.util.FileNameUniquifier;

/**
 * User: dax
 */
public class ReportNameContext extends FolderExportContext
{
    private String _serializedName;
    private final FileNameUniquifier _uniquifier = new FileNameUniquifier();

    public ReportNameContext(ImportContext context)
    {
        super(context.getUser(), context.getContainer(), context.getDataTypes(), context.getFormat(), context.getLoggerGetter());
    }

    public String getSerializedName()
    {
        return _serializedName;
    }

    // Generate a unique name for this report.
    // This name will be used in:
    // <name>.report.xml
    // <name> of the attachment directory
    // <name>.<extension> of the scriptfile
    public void generateSerializedName(ReportDescriptor d)
    {
        String reportName =  d.getReportName() != null ? d.getReportName() : d.getReportType();
        _serializedName = _uniquifier.uniquify(reportName);
    }
}
