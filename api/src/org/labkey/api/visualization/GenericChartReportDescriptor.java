/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.util.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 11, 2012
 */
public class GenericChartReportDescriptor extends VisualizationReportDescriptor
{
    public static final String TYPE = "GenericChartReportDescriptor";

    public enum Prop implements ReportProperty
    {
        renderType,
    }
    
    public GenericChartReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    public String getViewClass()
    {
        return "/org/labkey/visualization/views/genericChartWizard.jsp";
    }

    public Map<String, Object> getReportProps() throws Exception
        {
            Map<String, Object> props = new HashMap<String, Object>();
            List<Pair<DomainProperty, Object>> propsList = ReportPropsManager.get().getProperties(getEntityId(), getResourceContainer());
            if (propsList.size() > 0)
            {
                for (Pair<DomainProperty, Object> pair : propsList)
                    props.put(pair.getKey().getName(), pair.getValue());

                return props;
            }
            else
                return null;
        }

    @Override
    public boolean updateQueryNameReferences(Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        if (getJSON() != null)
        {
            // GenericChart JSON config usages of queryName in 13.1:
            // jsonData.queryConfig.queryName
            JSONObject json = new JSONObject(getJSON());
            JSONObject queryJson = json.getJSONObject("queryConfig");
            String queryName = queryJson.getString("queryName");
            if (queryName != null)
            {
                for (QueryChangeListener.QueryPropertyChange qpc : changes)
                {
                    if (queryName.equals(qpc.getOldValue()))
                    {
                        queryJson.put("queryName", qpc.getNewValue());
                        queryJson.put("queryLabel", ReportUtil.getQueryLabelByName(qpc.getSource().getSchema(), qpc.getSource().getName()));
                        setJSON(json.toString());
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
