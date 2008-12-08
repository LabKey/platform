/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

/*
* User: Dave
* Date: Dec 4, 2008
* Time: 2:43:52 PM
*/
public class ModuleReportLoader implements ModuleResourceLoader
{
    public static final String R_REPORT_EXTENSION = ".r";

    public void loadResources(Module module, File explodedModuleDir) throws IOException, ModuleResourceLoadException
    {
        File reportsDir = new File(explodedModuleDir, "reports");
        File schemasDir = new File(reportsDir, "schemas");

        if(schemasDir.exists())
        {
            for(File schemaDir : schemasDir.listFiles())
            {
                if(schemaDir.isDirectory())
                    loadReportsForSchema(module, schemaDir);
            }
        }
    }

    protected void loadReportsForSchema(Module module, File schemaDir) throws IOException, ModuleResourceLoadException
    {
        for(File queryDir : schemaDir.listFiles())
        {
            if(queryDir.isDirectory())
                loadReportsForQuery(module, schemaDir, queryDir);
        }
    }

    protected void loadReportsForQuery(Module module, File schemaDir, File queryDir) throws IOException, ModuleResourceLoadException
    {
        for(File file : queryDir.listFiles())
        {
            if(file.isFile())
            {
                String lowerName = file.getName().toLowerCase();
                if(lowerName.endsWith(R_REPORT_EXTENSION))
                    loadRReport(module, schemaDir, queryDir, file);
            }
        }
    }

    protected void loadRReport(Module module, File schemaDir, File queryDir, File reportFile) throws IOException, ModuleResourceLoadException
    {
        //currently the report service uses keys to identify reports, and those that
        //belong to a query are given the key '<schema>/<query>'. This should really
        //be encapsulated somewhere else, such as the ReportService?
        String reportKey = schemaDir.getName() + "/" + queryDir.getName();
        String reportName = FileUtil.getBaseName(reportFile);

        //get the file contents
        String script = getFileContents(reportFile);

        //build a module R report descriptor
        ModuleRReportDescriptor descriptor = new ModuleRReportDescriptor(reportKey, reportName, script, reportFile);

        //TODO: add to map of module reports maintained by ReportService

    }

    public static String getFileContents(File file) throws IOException
    {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder buffer = new StringBuilder();

        try
        {
            for(String line = reader.readLine(); null != line; line = reader.readLine())
            {
                buffer.append(line);
            }
        }
        finally
        {
            try {reader.close();} catch(IOException ignore){}
        }
        return buffer.toString();
    }
}