/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.security.ActionNames;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.view.*;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.query.QueryView;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.admin.AdminUrls;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import java.util.Map;

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
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "audit log", new ActionURL(ShowAuditLogAction.class, ContainerManager.getRoot()));
    }


    @ActionNames("begin, showAuditLog")
    @RequiresPermissionClass(AdminReadPermission.class)
    public class ShowAuditLogAction extends QueryViewAction<ShowAuditLogForm, QueryView>
    {
        public ShowAuditLogAction()
        {
            super(ShowAuditLogForm.class);
        }

        protected ModelAndView getHtmlView(ShowAuditLogForm form, BindException errors) throws Exception
        {
            if (!getViewContext().getUser().isAdministrator())
            {
                throw new UnauthorizedException();
            }

            VBox view = new VBox();

            JspView<String> jspView = new JspView<String>("/org/labkey/audit/auditLog.jsp", form.getView());

            view.addView(jspView);
            view.addView(createInitializedQueryView(form, errors, false, null));

            return view;
        }

        protected QueryView createQueryView(ShowAuditLogForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            String selected = form.getView();
            if (selected == null)
                selected = AuditLogService.get().getAuditViewFactories().get(0).getEventType();

            AuditLogService.AuditViewFactory factory = AuditLogService.get().getAuditViewFactory(selected);
            if (factory != null)
                return factory.createDefaultQueryView(getViewContext());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
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

    @RequiresPermissionClass(AdminPermission.class)
    public class ShowSiteSettingsAuditDetailsAction extends SimpleViewAction<SiteSettingsAuditDetailsForm>
    {
        public ModelAndView getView(SiteSettingsAuditDetailsForm form, BindException errors) throws Exception
        {
            if (null == form.getId() || form.getId().intValue() < 0)
                throw new NotFoundException("The audit log details key was not provided!");

            //get the audit event
            AuditLogEvent event = AuditLogService.get().getEvent(form.getId().intValue());

            if (null == event)
                throw new NotFoundException("Could not find the audit log event with id '" + form.getId().toString() + "'!");

            Map<String, Object> eventProps = OntologyManager.getProperties(ContainerManager.getSharedContainer(), event.getLsid());

            //create the model and view
            SiteSettingsAuditDetailsModel model = new SiteSettingsAuditDetailsModel(event, eventProps);
            return new JspView<SiteSettingsAuditDetailsModel>("/org/labkey/audit/siteSettingsAuditDetails.jsp", model);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL());

            ActionURL urlLog = new ActionURL(ShowAuditLogAction.class, ContainerManager.getRoot());
            urlLog.addParameter("view", WriteableAppProps.AUDIT_EVENT_TYPE);
            root = root.addChild("Audit Log", urlLog);
            return root.addChild("Site Settings Audit Event Details");
        }
    }
}
