/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.query.*;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.CompareType;
import org.apache.commons.lang.StringUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.sql.ResultSet;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ScriptEngine instance to execute the associated script.
*/
public abstract class ScriptEngineReport extends AbstractReport implements Report.ResultSetGenerator
{
    public static final String TYPE = "ReportService.scriptEngineReport";

    public String getType()
    {
        return TYPE;
    }

    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public ScriptEngine getScriptEngine()
    {
        String extension = getDescriptor().getProperty(RReportDescriptor.Prop.scriptExtension);
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);

        return mgr.getEngineByExtension(extension);
    }

    public String getTypeDescription()
    {
        ScriptEngine engine = getScriptEngine();
        if (engine != null)
        {
            return engine.getFactory().getLanguageName();
        }
        throw new RuntimeException("No Script Engine is available for this Report");
    }

    /**
     * Create the query view used to generate the result set that this report operates on.
     */
    protected QueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = (UserSchema) DefaultSchema.get(context.getUser(), context.getContainer()).getSchema(schemaName);
            QuerySettings settings = base.getSettings(context, dataRegionName);
            settings.setSchemaName(schemaName);
            settings.setQueryName(queryName);
            settings.setViewName(viewName);
            // need to reset the report id since we want to render the data grid, not the report
            settings.setReportId(null);

            UserSchema schema = base.createView(context, settings).getSchema();
            return new ReportQueryView(schema, settings);
        }
        return null;
    }

    public ResultSet generateResultSet(ViewContext context) throws Exception
    {
        ReportDescriptor descriptor = getDescriptor();
        QueryView view = createQueryView(context, descriptor);
        if (view != null)
        {
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            rgn.setMaxRows(0);
            RenderContext ctx = dataView.getRenderContext();

            // temporary code until we add a more generic way to specify a filter or grouping on the chart
            final String filterParam = descriptor.getProperty(ReportDescriptor.Prop.filterParam);
            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = (String)context.get(filterParam);
                if (filterValue != null)
                {
                    SimpleFilter filter = new SimpleFilter();
                    filter.addCondition(filterParam, filterValue, CompareType.EQUAL);

                    ctx.setBaseFilter(filter);
                }
            }
            return rgn.getResultSet(ctx);
        }
        return null;
    }
}
