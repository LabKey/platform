/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.module.Module;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

/*
* User: Dave
* Date: Dec 15, 2008
* Time: 1:03:17 PM
*/

/**
 * Represents an R report that comes from a module and is tied to a schema/query/view
 */
public class ModuleQueryRReportDescriptor extends ModuleRReportDescriptor
{
    private static Map<String,String> _reportTypeMap = new HashMap<String,String>();
    static
    {
        _reportTypeMap.put("ms1/Features", "MS1.R.Features");
        _reportTypeMap.put("ms1/Peaks", "MS1.R.Peaks");
        _reportTypeMap.put("ms2/Peptides", "MS2.SingleRun.rReport");
    }

    public ModuleQueryRReportDescriptor(Module module, String reportKey, File sourceFile, String reportPath)
    {
        super(module, reportKey, sourceFile, reportPath);

        if(null == getProperty(ReportDescriptor.Prop.schemaName))
        {
            //key is <schema-name>/<query-name>
            String[] keyParts = reportKey.split("/");
            if(keyParts.length >= 2)
            {
                setProperty(ReportDescriptor.Prop.schemaName, keyParts[0]);
                setProperty(ReportDescriptor.Prop.queryName, keyParts[1]);
            }
        }
    }

    public String getDefaultReportType(String reportKey)
    {
        //look in report type map
        String reportType = _reportTypeMap.get(reportKey);

        //if not found just return super
        return null != reportType ? reportType : super.getDefaultReportType(reportKey);
    }
}
