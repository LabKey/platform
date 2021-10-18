/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditUrls;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.TroubleShooterPermission;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Jul 24, 2008
 * Time: 4:07:39 PM
 */
public class AuditController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(AuditController.class);

    public AuditController()
    {
        setActionResolver(_actionResolver);
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "audit log", new ActionURL(ShowAuditLogAction.class, ContainerManager.getRoot()), CanSeeAuditLogPermission.class);
    }

    public static class AuditUrlsImpl implements AuditUrls
    {
        @Override
        public ActionURL getAuditLog(Container container, String eventType, @Nullable Date startDate, @Nullable Date endDate)
        {
            ActionURL url = new ActionURL(AuditLogAction.class, container).addParameter("eventType", eventType);

            if (startDate != null)
                url.addParameter("startDate", DateUtil.toISO(startDate));
            if (endDate != null)
                url.addParameter("endDate", DateUtil.toISO(endDate));

            return url;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        @Override
        public URLHelper getRedirectURL(Object o)
        {
            if (getContainer() != null && getContainer().isRoot())
                return new ActionURL(ShowAuditLogAction.class, getContainer());
            else
                return urlProvider(QueryUrls.class).urlSchemaBrowser(getContainer(), "auditLog");
        }
    }

    // An admin console action, but we want Troubleshooters to be able to POST (for export)
    @RequiresPermission(TroubleShooterPermission.class)
    public class ShowAuditLogAction extends QueryViewAction<ShowAuditLogForm, QueryView>
    {
        public ShowAuditLogAction()
        {
            super(ShowAuditLogForm.class);
        }

        @Override
        protected ModelAndView getHtmlView(ShowAuditLogForm form, BindException errors) throws Exception
        {
            VBox view = new VBox();

            JspView<String> jspView = new JspView<>("/org/labkey/audit/auditLog.jsp", form.getView());

            view.addView(jspView);
            view.addView(createInitializedQueryView(form, errors, false, null));

            return view;
        }

        @Override
        protected QueryView createQueryView(ShowAuditLogForm form, BindException errors, boolean forExport, String dataRegion)
        {
            // Troubleshooters don't have read permission, so add Reader as a contextual role to placate DataRegion's
            // and ButtonBar's render-time permissions check. See #39638
            if (!getContainer().hasPermission(getUser(), ReadPermission.class))
                getViewContext().addContextualRole(ReaderRole.class);

            String selected = form.getView();

            if (selected == null)
                selected = AuditLogService.get().getAuditProviders().get(0).getEventName();

            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, selected);
            settings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());

            return schema.createView(getViewContext(), settings, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("audits");
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Audit Log", getClass(), getContainer());
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
        @Override
        public ModelAndView getView(SiteSettingsAuditDetailsForm form, BindException errors)
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

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Admin Console", urlProvider(AdminUrls.class).getAdminConsoleURL());

            ActionURL urlLog = new ActionURL(ShowAuditLogAction.class, ContainerManager.getRoot());
            urlLog.addParameter("view", SiteSettingsAuditProvider.AUDIT_EVENT_TYPE);
            root.addChild("Audit Log", urlLog);
            root.addChild("Site Settings Audit Event Details");
        }
    }

    @SuppressWarnings({"unused"})
    @RequiresPermission(ReadPermission.class)
    public static class GetDetailedAuditChangesAction extends ReadOnlyApiAction<AuditChangesForm>
    {
        @Override
        public Object execute(AuditChangesForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DetailedAuditTypeEvent event = AuditLogService.get().getAuditEvent(getUser(), form.getAuditEventType(), form.getAuditRowId());

            if (event != null)
            {
                response.put("comment", event.getComment());
                response.put("eventUserId", event.getCreatedBy().getUserId());
                response.put("eventDateFormatted", new SimpleDateFormat(LookAndFeelProperties.getInstance(getContainer()).getDefaultDateTimeFormat()).format(event.getCreated()));

                String oldRecord = event.getOldRecordMap();
                String newRecord = event.getNewRecordMap();

                if (oldRecord != null || newRecord != null)
                {
                    response.put("oldData", AbstractAuditTypeProvider.decodeFromDataMap(oldRecord));
                    response.put("newData", AbstractAuditTypeProvider.decodeFromDataMap(newRecord));
                }

                response.put("success", true);
                return response;
            }

            response.put("success", false);
            return response;
        }
    }

    @SuppressWarnings({"unused"})
    @RequiresPermission(ReadPermission.class)
    public static class DetailedAuditChangesAction extends SimpleViewAction<AuditChangesForm>
    {
        @Override
        public ModelAndView getView(AuditChangesForm form, BindException errors)
        {
            int auditRowId = form.getAuditRowId();
            String comment = null;
            String oldRecord = null;
            String newRecord = null;

            DetailedAuditTypeEvent event = AuditLogService.get().getAuditEvent(getUser(), form.getAuditEventType(), auditRowId);

            if (event != null)
            {
                comment = event.getComment();
                oldRecord = event.getOldRecordMap();
                newRecord = event.getNewRecordMap();
            }

            if (oldRecord != null || newRecord != null)
            {
                Map<String,String> oldData = AbstractAuditTypeProvider.decodeFromDataMap(oldRecord);
                Map<String,String> newData = AbstractAuditTypeProvider.decodeFromDataMap(newRecord);

                return new AuditChangesView(comment, oldData, newData);
            }
            return new NoRecordView();
        }

        private static class NoRecordView extends HttpView
        {
            @Override
            protected void renderInternal(Object model, PrintWriter out)
            {
                out.write("<p>No current record found</p>");
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Audit Details");
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class AuditChangesForm
    {
        private int auditRowId;
        private String auditEventType;

        public int getAuditRowId()
        {
            return auditRowId;
        }

        public void setAuditRowId(int auditRowId)
        {
            this.auditRowId = auditRowId;
        }

        public String getAuditEventType()
        {
            return auditEventType;
        }

        public void setAuditEventType(String auditEventType)
        {
            this.auditEventType = auditEventType;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetTransactionRowIdsAction extends ReadOnlyApiAction<AuditTransactionForm>
    {
        @Override
        public void validateForm(AuditTransactionForm form, Errors errors)
        {
            form.validate(errors);
        }

        @Override
        public Object execute(AuditTransactionForm form, BindException errors) throws Exception
        {
            List<Integer> rowIds;
            if (form.isSampleType())
                rowIds = AuditLogImpl.get().getTransactionSampleIds(form.getTransactionAuditId(), getCanSeeAuditLogUser(getUser()), getContainer());
            else
                rowIds = AuditLogImpl.get().getTransactionSourceIds(form.getTransactionAuditId(), getUser(), getContainer());
            ;

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", true);
            response.put("rowIds", rowIds);

            return response;
        }

        private User getCanSeeAuditLogUser(User user)
        {
            User elevatedUser = user;
            if (!getContainer().hasPermission(getUser(), CanSeeAuditLogPermission.class))
            {
                Set<Role> contextualRoles = new HashSet<>(user.getStandardContextualRoles());
                contextualRoles.add(RoleManager.getRole(CanSeeAuditLogRole.class));
                elevatedUser = new LimitedUser(user, user.getGroups(), contextualRoles, false);
            }

            return elevatedUser;
        }
    }

    public static class AuditTransactionForm
    {
        private Long _transactionAuditId;
        private String _dataType;
        private boolean _isSampleType;

        public Long getTransactionAuditId()
        {
            return _transactionAuditId;
        }

        public void setTransactionAuditId(Long transactionAuditId)
        {
            _transactionAuditId = transactionAuditId;
        }

        public String getDataType()
        {
            return _dataType;
        }

        public void setDataType(String dataType)
        {
            _dataType = dataType;
        }

        public boolean isSampleType()
        {
            return _isSampleType;
        }

        public void validate(Errors errors)
        {
            if (getTransactionAuditId() == null)
                errors.reject(ERROR_REQUIRED, "'transactionAuditId' is required");
            if (getDataType() == null)
                errors.reject(ERROR_REQUIRED, "'dataType' is required");
            else
            {
                _isSampleType = getDataType().equalsIgnoreCase("samples");
                if (!_isSampleType && !getDataType().equalsIgnoreCase("sources"))
                    errors.reject(ERROR_MSG, "Unknown dataType: " + getDataType());
            }
        }
    }

    public static class AuditLogForm
    {
        private String _eventType;
        private Date _startDate;
        private Date _endDate;

        public String getEventType()
        {
            return _eventType;
        }

        public void setEventType(String eventType)
        {
            _eventType = eventType;
        }

        public Date getStartDate()
        {
            return _startDate;
        }

        public void setStartDate(Date startDate)
        {
            _startDate = startDate;
        }

        public Date getEndDate()
        {
            return _endDate;
        }

        public void setEndDate(Date endDate)
        {
            _endDate = endDate;
        }
    }

    @RequiresPermission(CanSeeAuditLogPermission.class)
    public class AuditLogAction extends SimpleViewAction<AuditLogForm>
    {
        private String _eventType;

        @Override
        public ModelAndView getView(AuditLogForm form, BindException errors) throws Exception
        {
            _eventType = form.getEventType();

            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, _eventType);

            SimpleFilter filter = new SimpleFilter();
            if (form.getStartDate() != null)
            {
                Calendar c = new GregorianCalendar();
                c.setTime(form.getStartDate());
                filter.addCondition(FieldKey.fromParts("created"), c, CompareType.DATE_GTE);
            }

            if (form.getEndDate() != null)
            {
                Calendar c = new GregorianCalendar();
                c.setTime(form.getEndDate());
                filter.addCondition(FieldKey.fromParts("created"), c, CompareType.DATE_LTE);
            }

            // add additional filters that may be on the URL
            filter.addUrlFilters(getViewContext().getActionURL(), QueryView.DATAREGIONNAME_DEFAULT);
            settings.setBaseFilter(filter);

            return schema.createView(getViewContext(), settings, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(_eventType + " : Audit Log");
        }
    }
}