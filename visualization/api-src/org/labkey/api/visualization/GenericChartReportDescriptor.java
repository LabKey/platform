/*
 * Copyright (c) 2012-2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
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
        super(TYPE);
    }

    @Override
    public String getViewClass()
    {
        return VIEW_CLASS;
    }

    @Override
    public Map<String, Object> getReportProps()
    {
        Map<String, Object> props = new HashMap<>();
        List<Pair<DomainProperty, Object>> propsList = ReportPropsManager.get().getProperties(getEntityId(), getResourceContainer());
        if (!propsList.isEmpty())
        {
            for (Pair<DomainProperty, Object> pair : propsList)
                props.put(pair.getKey().getName(), pair.getValue());

            return props;
        }
        else
            return null;
    }

    @Override
    public @Nullable String getPointClickFn(@Nullable String json)
    {
        if (StringUtils.isBlank(json))
            return null;
        JSONObject chartConfig = new JSONObject(json).optJSONObject("chartConfig");
        if (chartConfig == null)
            return null;

        JSONObject measures = chartConfig.optJSONObject("measures");
        return measures != null ? StringUtils.trimToNull(measures.optString("pointClickFn", null)) : null;
    }

    @Override
    public boolean updateSchemaQueryNameReferences(Collection<QueryChangeListener.QueryPropertyChange<?>> changes, User user, Container container, boolean isSchemaUpdate)
    {
        if (getJSON() != null)
        {
            // GenericChart JSON config usages of queryName in 13.1:
            // jsonData.queryConfig.queryName
            JSONObject json = new JSONObject(getJSON());
            JSONObject queryJson = json.getJSONObject("queryConfig");
            String queryName = queryJson.optString("queryName", null);
            String schemaName = queryJson.optString("schemaName", null);
            if ((queryName != null && !isSchemaUpdate) || (schemaName != null && isSchemaUpdate))
            {
                for (QueryChangeListener.QueryPropertyChange<?> qpc : changes)
                {
                    if (isSchemaUpdate)
                    {
                        if (schemaName.equals(qpc.getOldValue()))
                        {
                            queryJson.put("schemaName", qpc.getNewValue());
                            queryJson.put("queryLabel", ReportUtil.getQueryLabelByName(user, container, qpc.getNewValue().toString(), queryName));
                            setJSON(json.toString());
                            return true;
                        }
                    }
                    else
                    {
                        if (queryName.equals(qpc.getOldValue()) && qpc.getSource() != null)
                        {
                            queryJson.put("queryName", qpc.getNewValue());
                            queryJson.put("queryLabel", ReportUtil.getQueryLabelByName(qpc.getSource().getSchema(), qpc.getSource().getName()));
                            setJSON(json.toString());
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
