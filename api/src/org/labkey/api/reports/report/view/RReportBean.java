/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.query.QuerySettings;
import org.labkey.common.util.Pair;

import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
* User: Karl Lum
* Date: Dec 4, 2007
*/
public class RReportBean extends ScriptReportBean
{
    protected List<String> _includedReports;

    public RReportBean(){}
    public RReportBean(QuerySettings settings)
    {
        super(settings);
    }
    
    public void setIncludedReports(List<String> includedReports)
    {
        _includedReports = includedReports;
    }

    public List<String> getIncludedReports()
    {
        return _includedReports != null ? _includedReports : Collections.EMPTY_LIST;
    }

    public Report getReport() throws Exception
    {
        Report report = super.getReport();

        if (report != null)
        {
            ReportDescriptor descriptor = report.getDescriptor();
            if (RReportDescriptor.class.isAssignableFrom(descriptor.getClass()))
            {
                ((RReportDescriptor)descriptor).setIncludedReports(_includedReports);
            }
            else
                return null;
        }
        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = super.getParameters();

        for (String report : getIncludedReports())
            list.add(new Pair<String, String>(RReportDescriptor.Prop.includedReports.toString(), report));

        return list;
    }
}
