/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.labkey.api.view.template.ClientDependency;

import java.util.*;

/*
* User: adam
* Date: Jan 12, 2011
* Time: 5:32:12 PM
*/
abstract public class ScriptReportDescriptor extends ReportDescriptor
{
    public static final String REPORT_METADATA_EXTENSION = ".report.xml";

    public enum Prop implements ReportProperty
    {
        script,
        scriptExtension,
        includedReports,
        runInBackground,
        sourceTabVisible,
        knitrFormat
    }

    public void setIncludedReports(List<String> reports)
    {
        _props.put(Prop.includedReports.toString(), reports);
    }

    public List<String> getIncludedReports()
    {
        Object reports = _props.get(Prop.includedReports.toString());
        if (reports != null && List.class.isAssignableFrom(reports.getClass()))
            return (List<String>)reports;

        return Collections.emptyList();
    }

    public boolean isArrayType(String prop)
    {
        if (!super.isArrayType(prop))
        {
            return Prop.includedReports.toString().equals(prop);
        }
        return true;
    }
}
