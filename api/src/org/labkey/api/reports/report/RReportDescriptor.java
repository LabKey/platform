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

package org.labkey.api.reports.report;

import org.labkey.api.view.ViewContext;

import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jul 12, 2007
 */
public class RReportDescriptor extends ReportDescriptor
{
    public static final String TYPE = "rReportDescriptor";

    public enum Prop implements ReportProperty
    {
        runInBackground,
        includedReports,
        script,
        scriptExtension,
    }

    public RReportDescriptor()
    {
        setDescriptorType(TYPE);
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

        return Collections.EMPTY_LIST;
    }
    
    public boolean isArrayType(String prop)
    {
        if (!super.isArrayType(prop))
        {
            return Prop.includedReports.toString().equals(prop);
        }
        return true;
    }

    public boolean canEdit(ViewContext context)
    {
        if (RReport.canCreateScript(context))
        {
            return super.canEdit(context.getUser(), context.getContainer());
        }
        return false;
    }
}
