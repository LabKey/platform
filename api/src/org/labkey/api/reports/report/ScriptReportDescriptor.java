/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
import org.labkey.query.xml.ReportDescriptorDocument;

import java.util.Collections;
import java.util.List;
import java.util.Set;

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
        scriptDependencies,
        includedReports,
        runInBackground,
        sourceTabVisible,
        knitrFormat,
        useGetDataApi,
        rmarkdownOutputOptions, /* pandoc only */
        useDefaultOutputFormat /* pandoc only */
    }

    public ScriptReportDescriptor()
    {
        super();
    }

    public ScriptReportDescriptor(String descriptorType)
    {
        super(descriptorType);
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

    public String getScriptDependencies()
    {
        return (String) _props.get(Prop.scriptDependencies.toString());
    }

    public void setScriptDependencies(String scriptDependencies)
    {
        _props.put(Prop.scriptDependencies.toString(), scriptDependencies);
    }

    @Override
    public boolean isArrayType(String prop)
    {
        if (!super.isArrayType(prop))
        {
            return Prop.includedReports.toString().equals(prop);
        }
        return true;
    }

    @Override
    public ReportDescriptorDocument getDescriptorDocument(FolderExportContext context)
    {
        // if we are doing folder export (or module file save), we don't want to double save the script property
        return getDescriptorDocument(context.getContainer(), context, true, Set.of(Prop.script.name()));
    }
}
