/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.LabKeyError;
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
import org.labkey.api.reports.Report;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

/**
 * User: Karl Lum
 * Date: Oct 6, 2006
 */
public class QueryReport extends AbstractReport
{
    public static final String TYPE = "ReportService.queryReport";

    public static final String SCHEMA_NAME = "schemaName";
    public static final String QUERY_NAME = "queryName";
    public static final String VIEW_NAME = "viewName";

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

    public void setSchemaName(String schemaName)
    {
        getDescriptor().setProperty(SCHEMA_NAME, schemaName);
    }

    public void setQueryName(String queryName)
    {
        getDescriptor().setProperty(QUERY_NAME, queryName);
    }

    public void setViewName(String viewName)
    {
        getDescriptor().setProperty(VIEW_NAME, viewName);
    }

    public HttpView renderReport(ViewContext context)
    {
        if ("true".equals(context.get(renderParam.reportWebPart.name())))
        {
            return new JspView<Report>("/org/labkey/api/reports/report/view/renderQueryReport.jsp", this);
        }
        else
        {
            BindException errors = new NullSafeBindException(this, "form");
            QueryView view = createQueryView(context, errors);
            StringBuilder sb = new StringBuilder();

            if (errors.hasErrors())
            {
                sb.append("Unable to display the report");
                for (ObjectError error : errors.getAllErrors())
                {
                    sb.append("\n");
                    sb.append(error.getDefaultMessage());
                }
            }

            if (view != null && sb.length() == 0)
                return view;

            if (sb.length() > 0)
            {
                return new HtmlView("<span class=\"labkey-error\">" + PageFlowUtil.filter(sb.toString(), true, false) + "</span>");
            }
            return null;
        }
    }

    protected  UserSchema getSchema(ViewContext context, String schemaName)
    {
        return QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
    }

    public QueryView createQueryView(ViewContext context, BindException errors)
    {
        String schemaName = getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
        String queryName = getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
        String viewName = getDescriptor().getProperty(ReportDescriptor.Prop.viewName);

        // query reports can be rendered within another data region, need to make sure inner and outer data regions are unique
        String dataRegionName = StringUtils.defaultString(getDescriptor().getProperty(ReportDescriptor.Prop.dataRegionName), QueryView.DATAREGIONNAME_DEFAULT) + "_" + getReportId().toString();

        if (schemaName == null)
            errors.addError(new LabKeyError("schema name cannot be empty"));
        if (queryName == null)
            errors.addError(new LabKeyError("query name cannot be empty"));

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
            //settings.setAllowCustomizeView(false);

            final String filterParam = getDescriptor().getProperty("filterParam");

            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = context.getActionURL().getParameter(filterParam);

                if (filterValue != null)
                    settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts(filterParam), filterValue));
            }

            QueryView view = schema.createView(context, settings, errors);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);

            return view;
        }
        else
            errors.addError(new LabKeyError("unable to create the schema for this query configuration"));

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
            return QueryService.get().getCustomView(context.getUser(), context.getContainer(), context.getUser(), schemaName, queryName, viewName);

        return null;
    }

    @Override
    public Thumbnail generateThumbnail(@Nullable ViewContext context)
    {
        return null;
    }

    @Override
    public String getStaticThumbnailPath()
    {
        return "/query/query.png";
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        return PageFlowUtil.urlProvider(ReportUrls.class).urlQueryReport(context.getContainer(), this);
    }
}
