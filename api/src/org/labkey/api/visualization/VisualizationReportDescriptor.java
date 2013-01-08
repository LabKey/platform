/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.api.visualization;

import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.JspView;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * User: brittp
 * Date: Feb 3, 2011 11:15:10 AM
 */
public class VisualizationReportDescriptor extends ReportDescriptor
{
    public String getJSON()
    {
        return getProperty(Prop.json);
    }

    public void setJSON(String json)
    {
        setProperty(Prop.json, json);
    }

    public Map<String, Object> getReportProps() throws Exception
    {
        return null;
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> d = super.getClientDependencies();
        JspView v = new JspView(getViewClass());
        d.addAll(v.getClientDependencies());
        return d;
    }
}
