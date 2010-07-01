/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.query.*;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletResponse;

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
        ReportDescriptor reportDescriptor = getDescriptor();

        String errorMessage = null;
        if (reportDescriptor instanceof QueryReportDescriptor)
        {
            try {
                final QueryReportDescriptor descriptor = (QueryReportDescriptor)reportDescriptor;
                QueryReportDescriptor.QueryViewGenerator qvGen = getQueryViewGenerator();
                if (qvGen == null)
                {
                    qvGen = descriptor.getQueryViewGenerator();
                }

                if (qvGen != null)
                {
                    ReportQueryView qv = qvGen.generateQueryView(context, descriptor);
                    if (qv != null)
                    {
                        final UserSchema schema = qv.getQueryDef().getSchema();
                        if (schema != null)
                        {
                            String queryName = descriptor.getProperty("queryName");
                            if (queryName != null)
                            {
                                String viewName = descriptor.getProperty(QueryParam.viewName.toString());
                                QuerySettings qs = new QuerySettings(context, "Report");
                                qs.setSchemaName(schema.getSchemaName());
                                qs.setQueryName(queryName);
                                QueryDefinition queryDef = qv.getQueryDef();
                                if (queryDef.getCustomView(null, context.getRequest(), viewName) == null)
                                {
                                    CustomView view = queryDef.createCustomView(null, viewName);
                                    view.setIsHidden(true);
                                    view.save(context.getUser(), context.getRequest());
                                }
                                qs.setViewName(viewName);
                                //ReportQueryView queryReportView = new ReportQueryView(context, schema, qs);
                                //JspView<HeaderBean> headerView = new JspView<HeaderBean>("/org/labkey/query/reports/view/queryReportHeader.jsp",
                                //        new HeaderBean(context, qv.getCustomizeURL()));
                                return qv;
                            }
                            else
                            {
                                errorMessage = "Invalid report params: the queryName must be specified in the QueryReportDescriptor";
                            }
                        }
                    }
                }
                else
                {
                    errorMessage = "Invalid report params: A query view generator has not been specified through the ReportDescriptor";
                }
            }
            catch (Exception e)
            {
                errorMessage = e.getMessage();
            }
        }
        else
        {
            errorMessage = "Invalid report params: The ReportDescriptor must be an instance of QueryReportDescriptor";
        }

        if (errorMessage != null)
        {
            return new VBox(ExceptionUtil.getErrorView(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage, null, context.getRequest(), false));
        }
        return null;
    }

    public static class HeaderBean
    {
        private ViewContext _viewContext;
        private ActionURL _customizeURL;

        public HeaderBean(ViewContext context, ActionURL customizeURL)
        {
            _viewContext = context;
            _customizeURL = customizeURL;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public boolean showCustomizeLink()
        {
            return _viewContext.getContainer().hasPermission(_viewContext.getUser(), AdminPermission.class);
        }

        public ActionURL getCustomizeURL()
        {
            return _customizeURL;
        }
    }

    public QueryReportDescriptor.QueryViewGenerator getQueryViewGenerator()
    {
        return null;
    }
}
