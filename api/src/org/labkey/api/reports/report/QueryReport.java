/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 6, 2006
 */
public class QueryReport extends AbstractReport
{
    public static final String TYPE = "ReportService.queryReport";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Data Grid View";
    }

    public String getDescriptorType()
    {
        return QueryReportDescriptor.TYPE;
    }

    public HttpView renderReport(ViewContext context)
    {
        BindException errors = new NullSafeBindException(this, "form");
        QueryView view = createQueryView(context, errors);
        StringBuilder sb = new StringBuilder();
        String delim = "";

        if (errors.hasErrors())
        {
            for (ObjectError error : (List<ObjectError>)errors.getAllErrors())
            {
                sb.append(delim);
                sb.append(error.getDefaultMessage());
                delim = "\n";
            }
        }

        if (view != null && sb.length() == 0)
            return view;
        else
        {
            sb.append(delim);
            sb.append("Unable to render the report");
        }

        if (sb.length() > 0)
        {
            return ExceptionUtil.getErrorView(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, sb.toString(), null, context.getRequest(), false);
        }
        return null;
    }

    protected  UserSchema getSchema(ViewContext context, String schemaName)
    {
        return QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
    }

    private QueryView createQueryView(ViewContext context, BindException errors)
    {
        String schemaName = getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
        String queryName = getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
        String viewName = getDescriptor().getProperty(ReportDescriptor.Prop.viewName);
        String dataRegionName = getDescriptor().getProperty(ReportDescriptor.Prop.dataRegionName);

        UserSchema schema = getSchema(context, schemaName);

        if (schema != null)
        {
            QuerySettings settings = schema.getSettings(context, dataRegionName, queryName);
            settings.setViewName(viewName);
            // need to reset the report id since we want to render the data grid, not the report
            settings.setReportId(null);
            // by default we want all rows since the data may be used for a chart, grid based reports will ask for paging
            // at the report level.
            settings.setMaxRows(Table.ALL_ROWS);
            settings.setAllowCustomizeView(false);

            final String filterParam = getDescriptor().getProperty("filterParam");

            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = context.getActionURL().getParameter(filterParam);

                if (filterValue != null)
                    settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts(filterParam), filterValue));
            }

            QueryView view = schema.createView(context, settings, errors);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

            return view;
        }
        return null;
    }

    public QueryReportDescriptor.QueryViewGenerator getQueryViewGenerator()
    {
        return null;
    }

    protected CustomView getCustomView(ContainerUser context)
    {
        String schemaName = getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
        String queryName = getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
        String viewName = getDescriptor().getProperty(ReportDescriptor.Prop.viewName);

        if (viewName != null)
            return QueryService.get().getCustomView(context.getUser(), context.getContainer(), schemaName, queryName, viewName);

        return null;
    }

    public void beforeDelete(ContainerUser context)
    {
        CustomView view = getCustomView(context);
        if (view != null)
        {
            HttpServletRequest request = new MockHttpServletRequest();
            view.delete(context.getUser(), request);
        }
        super.beforeDelete(context);
    }
}
