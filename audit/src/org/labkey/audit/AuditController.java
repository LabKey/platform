/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.audit;

import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.User;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.*;
import org.labkey.api.query.QueryView;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.admin.AdminUrls;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import java.util.Date;

/**
 * User: adam
 * Date: Jul 24, 2008
 * Time: 4:07:39 PM
 */
public class AuditController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(AuditController.class);

    public AuditController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "audit log", new ActionURL(ShowAuditLogAction.class, ContainerManager.getRoot()), CanSeeAuditLogPermission.class);
    }

    @RequiresPermission(AdminPermission.class)
    public class BeginAction extends RedirectAction
    {
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            if (getContainer() != null && getContainer().isRoot())
                return new ActionURL(ShowAuditLogAction.class, getContainer());
            else
                return PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(getContainer(), "auditLog");
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            return true;
        }
    }

    @ActionNames("showAuditLog")
    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class ShowAuditLogAction extends QueryViewAction<ShowAuditLogForm, QueryView>
    {
        public ShowAuditLogAction()
        {
            super(ShowAuditLogForm.class);
        }

        protected ModelAndView getHtmlView(ShowAuditLogForm form, BindException errors) throws Exception
        {
            VBox view = new VBox();

            JspView<String> jspView = new JspView<>("/org/labkey/audit/auditLog.jsp", form.getView());

            view.addView(jspView);
            view.addView(createInitializedQueryView(form, errors, false, null));

            return view;
        }

        protected QueryView createQueryView(ShowAuditLogForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            String selected = form.getView();

            if (selected == null)
                selected = AuditLogService.get().getAuditProviders().get(0).getEventName();

            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, selected);
            settings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());

            return schema.createView(getViewContext(), settings, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("audits"));
            return PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Audit Log", getURL());
        }

        public ActionURL getURL()
        {
            return new ActionURL(ShowAuditLogAction.class, ContainerManager.getRoot());
        }
    }

    public static class ShowAuditLogForm extends QueryViewAction.QueryExportForm
    {
        private String _view;

        public String getView()
        {
            return _view;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setView(String view)
        {
            _view = view;
        }
    }


    public static class SiteSettingsAuditDetailsForm
    {
        private Integer _id;

        public Integer getId()
        {
            return _id;
        }

        public void setId(Integer id)
        {
            _id = id;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ShowSiteSettingsAuditDetailsAction extends SimpleViewAction<SiteSettingsAuditDetailsForm>
    {
        public ModelAndView getView(SiteSettingsAuditDetailsForm form, BindException errors) throws Exception
        {
            if (null == form.getId() || form.getId().intValue() < 0)
                throw new NotFoundException("The audit log details key was not provided!");

            String diff = null;
            User createdBy = null;
            Date created = null;

            SiteSettingsAuditProvider.SiteSettingsAuditEvent event = AuditLogService.get().getAuditEvent(getUser(), SiteSettingsAuditProvider.AUDIT_EVENT_TYPE, form.getId());

            if (event != null)
            {
                diff = event.getChanges();
                createdBy = event.getCreatedBy();
                created = event.getCreated();
            }

            SiteSettingsAuditDetailsModel model = new SiteSettingsAuditDetailsModel(getContainer(), diff, createdBy, created);
            return new JspView<>("/org/labkey/audit/siteSettingsAuditDetails.jsp", model);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL());

            ActionURL urlLog = new ActionURL(ShowAuditLogAction.class, ContainerManager.getRoot());
            urlLog.addParameter("view", SiteSettingsAuditProvider.AUDIT_EVENT_TYPE);
            root = root.addChild("Audit Log", urlLog);
            return root.addChild("Site Settings Audit Event Details");
        }
    }
}
