/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import javax.servlet.ServletException;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Apr 24, 2007
 */
public class ReportQueryViewFactory
{
    private static ReportQueryViewFactory _instance = new ReportQueryViewFactory();

    public static ReportQueryViewFactory get()
    {
        return _instance;
    }

    private ReportQueryViewFactory(){}

    public ReportQueryView generateQueryView(ViewContext context, ReportDescriptor descriptor,
                                                    String queryName, String viewName) throws Exception
    {
        StudyQuerySchema schema = getStudyQuerySchema(context, ReadPermission.class, descriptor);

        if (schema != null)
        {
            QuerySettings settings = schema.getSettings(context, descriptor.getProperty(QueryParam.dataRegionName.toString()), queryName);
            settings.setViewName(viewName);
            // need to reset the report id since we want to render the data grid, not the report
            settings.setReportId(null);
            // by default we want all rows since the data may be used for a chart, grid based reports will ask for paging
            // at the report level.
            settings.setMaxRows(Table.ALL_ROWS);


            ReportQueryView view = new StudyReportQueryView(schema, settings);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
            final String filterParam = descriptor.getProperty("filterParam");

            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = context.getActionURL().getParameter(filterParam);

                if (filterValue != null)
                {
                    view.setFilter(new SimpleFilter(filterParam, filterValue));
                }
            }

            return view;
        }

        return null;
    }

    /** return true if report should do regular permission checking
     * by checking ACLs on underlying datasets using dataset.getTableInfo(user)
     * false if no permission checking is required.  and throws if
     * user does not have permission.
     */
    private static boolean mustCheckDatasetPermissions(User user, @NotNull Class<? extends Permission> perm, ReportDescriptor descriptor) throws ServletException
    {
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(descriptor, false);
        if (policy.isEmpty())
            return true;    // normal permission checking

        if (!policy.hasPermission(user, perm))
            throw new UnauthorizedException();

        return false;   // user is OK, don't check permissions
    }

    private static StudyQuerySchema getStudyQuerySchema(ContainerUser context, @NotNull Class<? extends Permission> perm, ReportDescriptor descriptor) throws ServletException
    {
        if (perm != ReadPermission.class)
            throw new IllegalArgumentException("only PERM_READ supported");
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        boolean mustCheckUserPermissions = mustCheckDatasetPermissions(context.getUser(), perm, descriptor);

        if (study != null)
            return StudyQuerySchema.createSchema(study, context.getUser(), mustCheckUserPermissions);
        return null;
    }

    public static StudyQuerySchema getStudyQuerySchema(ContainerUser context, ReportDescriptor descriptor) throws ServletException
    {
        return getStudyQuerySchema(context, ReadPermission.class, descriptor);
    }

    public static class StudyReportQueryView extends ReportQueryView
    {
        public StudyReportQueryView(UserSchema schema, QuerySettings settings)
        {
            super(schema, settings);
        }

        public MenuButton createViewButton(ReportService.ItemFilter filter)
        {
            MenuButton button = super.createViewButton(StudyReportUIProvider.getItemFilter());
            String id = getViewContext().getRequest().getParameter(DatasetDefinition.DATASETKEY);
            if (id != null)
                button.addMenuItem("Set Default", getViewContext().cloneActionURL().setAction(StudyController.ViewPreferencesAction.class));

            return button;
        }

        public void addCustomizeViewItems(MenuButton button)
        {
            NavTree customizeView = new NavTree("Customize Grid");
            customizeView.setId(getBaseMenuId() + ":GridViews:Customize Grid");
            customizeView.setScript(DataRegion.getJavaScriptObjectReference(getDataRegionName()) + ".toggleShowCustomizeView();");
            customizeView.setImageCls("fa fa-pencil");
            button.addMenuItem(customizeView);
        }

        @Override
        protected boolean canViewReport(User user, Container c, Report report)
        {
            return ReportManager.get().canReadReport(getUser(), getContainer(), report);
        }
        
        @Override
        public void addManageViewItems(MenuButton button, Map<String, String> params)
        {
            ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getViewContext().getContainer());
            for (Map.Entry<String, String> entry : params.entrySet())
                url.addParameter(entry.getKey(), entry.getValue());

            button.addMenuItem("Manage Views", url);
        }
    }
}
