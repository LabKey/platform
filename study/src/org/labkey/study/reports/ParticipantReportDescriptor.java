/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.study.reports;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.JspView;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * User: adam
 * Date: 8/16/12
 * Time: 6:41 AM
 */
public class ParticipantReportDescriptor extends ReportDescriptor
{
    public static final String TYPE = "participantReportDescriptor";

    public ParticipantReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    @Override
    protected String adjustPropertyValue(@Nullable ImportContext context, String key, Object value)
    {
        if (null != context && context.isAlternateIds() && "groups".equals(key))
        {
            Map<String, String> alternateIdMap = StudyService.get().getAlternateIdMap(context.getContainer());   // Translate the IDs
            JSONArray json = new JSONArray((String) value);
            JSONArray transformedJson = new JSONArray();

            for (int i = 0; i < json.length(); i++)
            {
                JSONObject item = json.getJSONObject(i);
                if (item.get("type").equals("participant"))
                {
                    String newId = alternateIdMap.get(item.get("id"));
                    item.put("id", newId);
                    item.put("label", newId);
                    transformedJson.put(item);
                }
            }

            value = transformedJson.toString();
        }

        return super.adjustPropertyValue(context, key, value);
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> d = super.getClientDependencies();
        JspView v = new JspView(getViewClass());
        d.addAll(v.getClientDependencies());
        return d;
    }

    @Override
    public String getViewClass()
    {
        return "/org/labkey/study/view/participantReport.jsp";
    }
}
