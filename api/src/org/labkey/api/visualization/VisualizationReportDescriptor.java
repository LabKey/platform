/*
 * Copyright (c) 2011-2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.view.JspView;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Feb 3, 2011 11:15:10 AM
 */
abstract public class VisualizationReportDescriptor extends ReportDescriptor
{
    public static final String VIEW_CLASS = "/org/labkey/visualization/views/chartWizard.jsp";

    protected VisualizationReportDescriptor(String type)
    {
        super(type);
    }

    public String getJSON()
    {
        return getProperty(Prop.json);
    }

    public void setJSON(String json)
    {
        setProperty(Prop.json, json);
    }

    public Map<String, Object> getReportProps()
    {
        return null;
    }

    /**
     * Source of the point click handler in the given chart config, which the client evaluates in every viewer's browser.
     * @throws org.json.JSONException if the config can't be parsed
     */
    public @Nullable String getPointClickFn(@Nullable String json)
    {
        return null;
    }

    public @Nullable String getPointClickFn()
    {
        return getPointClickFn(getJSON());
    }

    /** GH Issue 1649: only trusted browser developers may add or change the point click function in {@code json}. */
    public void validatePointClickFn(User user, @Nullable String json, @Nullable ReportDescriptor stored, List<ValidationError> errors)
    {
        if (user.isTrustedBrowserDev())
            return;

        String fn;
        try
        {
            fn = getPointClickFn(json);
        }
        catch (JSONException e)
        {
            // Fail closed: the browser's JSON parser may read a config org.json rejects (e.g. duplicate keys)
            errors.add(new SimpleValidationError("Invalid chart configuration: " + e.getMessage()));
            return;
        }

        if (fn == null)
            return;

        String storedFn = null;
        if (stored instanceof VisualizationReportDescriptor storedDescriptor)
        {
            try
            {
                storedFn = storedDescriptor.getPointClickFn();
            }
            catch (JSONException ignored)
            {
            }
        }

        if (!fn.equals(storedFn))
            errors.add(new SimpleValidationError("Only trusted developers can add or change a chart's point click function."));
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> d = super.getClientDependencies();
        JspView v = new JspView<>(getViewClass());
        d.addAll(v.getClientDependencies());
        return d;
    }
}
