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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DefaultViewInfo;
import org.labkey.api.data.views.ProviderType;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Apr 2, 2012
 */
public class ReportViewProvider implements DataViewProvider
{
    private static final DataViewProvider.Type _type = new ProviderType("reports", "Report Views", true);

    public static DataViewProvider.Type getType()
    {
        return _type;
    }

    @Override
    public boolean isVisible(Container container, User user)
    {
        return true;
    }

    @Override
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        Container container = context.getContainer();
        User user = context.getUser();

        if (isVisible(container, user))
        {
            //boolean isAdmin = container.hasPermission(user, AdminPermission.class);
            ReportUtil.ReportFilter filter = new ReportUtil.DefaultReportFilter();

            if (StudyService.get().getStudy(container) != null)
                filter = new ReportManager.StudyReportFilter(false);

            return getViews(context, null, null, filter);
        }
        return Collections.emptyList();
    }

    private List<DataViewInfo> getViews(ViewContext context, String schemaName, String queryName, ReportUtil.ReportFilter filter)
    {
        Container c = context.getContainer();
        User user = context.getUser();

        if (filter == null)
            throw new IllegalArgumentException("ReportFilter cannot be null");

        String reportKey = null;

        if (schemaName != null && queryName != null)
            reportKey = ReportUtil.getReportKey(schemaName, queryName);

        List<DataViewInfo> views = new ArrayList<DataViewInfo>();

        try
        {
            ReportPropsManager.get().ensureProperty(c, user, "status", "Status", PropertyType.STRING);
            ReportPropsManager.get().ensureProperty(c, user, "author", "Author", PropertyType.INTEGER);
            ReportPropsManager.get().ensureProperty(c, user, "refreshDate", "RefreshDate", PropertyType.DATE_TIME);

            for (Report r : ReportUtil.getReports(c, user, reportKey, true))
            {
                if (!filter.accept(r, c, user))
                    continue;

                if (!StringUtils.isEmpty(r.getDescriptor().getReportName()))
                {
                    ReportDescriptor descriptor = r.getDescriptor();

                    User createdBy = UserManager.getUser(descriptor.getCreatedBy());
                    User modifiedBy = UserManager.getUser(descriptor.getModifiedBy());
                    Object authorId = ReportPropsManager.get().getPropertyValue(descriptor.getEntityId(), c, "author");

                    User author = authorId != null ? UserManager.getUser(((Double)authorId).intValue()) : null;
                    boolean inherited = descriptor.isInherited(c);

                    DefaultViewInfo info = new DefaultViewInfo(_type, descriptor.getEntityId(), descriptor.getReportName(), descriptor.lookupContainer());

                    String query = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                    String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);

                    if (descriptor.getCategory() != null)
                        info.setCategory(descriptor.getCategory());
                    else
                        info.setCategory(ReportUtil.getDefaultCategory(c, schema, query));

                    info.setCreatedBy(createdBy);
                    info.setCreated(descriptor.getCreated());
                    info.setModifiedBy(modifiedBy);
                    info.setAuthor(author);
                    info.setModified(descriptor.getModified());

                    /**
                     * shared reports are only available if there is a query/schema available in the container that matches
                     * the view's descriptor. Normally, the check happens automatically when you get reports using a non-blank key, but when
                     * you request all reports for a container you have to do an explicit check to make sure there is a valid query
                     * available in the container.
                     */
                    if (!inherited || !StringUtils.isBlank(reportKey))
                    {
                        ActionURL runUrl = r.getRunReportURL(context);
                        ActionURL detailsUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlReportDetails(c, r);

                        info.setRunUrl(runUrl);
                        info.setDetailsUrl(detailsUrl);
                    }
                    else
                    {
                        ActionURL runUrl = r.getRunReportURL(context);

                        if (ReportUtil.queryExists(user, c, schema, query))
                            info.setRunUrl(runUrl);
                        else
                            continue;
                    }
                    info.setType(r.getTypeDescription());
                    info.setDescription(descriptor.getReportDescription());

                    String access;
                    if (descriptor.getOwner() != null)
                    {
                        access = "private";
                        info.setShared(false);
                    }
                    else if (!(descriptor instanceof ModuleRReportDescriptor) && !org.labkey.api.security.SecurityManager.getPolicy(descriptor, false).isEmpty())
                    {
                        // FIXME: see 10473: ModuleRReportDescriptor extends securable resource, but doesn't properly implement it.  File-based resources don't have a Container or Owner.
                        access = "custom"; // 13571: Explicit is a bad name for custom permissions
                        info.setShared(false);
                    }
                    else
                    {
                        access = "public";
                        info.setShared(true);
                    }
                    info.setAccess(access);
                    info.setVisible(!descriptor.isHidden());

                    // This icon is the small icon -- not the same as thumbnail
                    String iconPath = ReportService.get().getIconPath(r);

                    // No way for a report to offer a specific icon based on its content, so do this hack for attachment reports  TODO: fix
                    if ("Study.attachmentReport".equals(r.getType()))
                    {
                        String filename = r.getDescriptor().getProperty("filePath");

                        if (null == filename)
                        {
                            List<Attachment> list = AttachmentService.get().getAttachments(r);
                            filename = list.isEmpty() ? "" : list.get(0).getName();
                        }

                        iconPath = PageFlowUtil.urlProvider(CoreUrls.class).getAttachmentIconURL(c, filename).toString();
                    }

                    if (!StringUtils.isEmpty(iconPath))
                        info.setIcon(iconPath);

                    info.setThumbnailUrl(PageFlowUtil.urlProvider(ReportUrls.class).urlThumbnail(c, r));
                    info.setTags(ReportPropsManager.get().getProperties(descriptor.getEntityId(), context.getContainer()));

                    views.add(info);
                }
            }
            return views;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public EditInfo getEditInfo()
    {
        return new EditInfoImpl();
    }

    public static class EditInfoImpl implements DataViewProvider.EditInfo
    {
        private static final String[] _editableProperties = {
                Property.description.name(),
                Property.category.name(),
                Property.visible.name(),
                Property.author.name(),
                Property.status.name(),
                Property.refreshDate.name(),
                Property.shared.name(),
        };

        @Override
        public String[] getEditableProperties(Container container, User user)
        {
            return _editableProperties;
        }

        @Override
        public void validateProperties(Container container, User user, String id, Map<String, Object> props) throws ValidationException
        {
            try {
                Report report = ReportService.get().getReportByEntityId(container, id);

                if (report != null)
                {
                    List<ValidationError> errors = new ArrayList<ValidationError>();

                    if (!report.canEdit(user, container, errors))
                    {
                        String errorMsg = ReportUtil.getErrors(errors);
                        throw new ValidationException(errorMsg);
                    }
                }
            }
            catch (SQLException e)
            {
                throw new ValidationException("Unable to find the specified report view");
            }
        }

        @Override
        public void updateProperties(Container container, User user, String id, Map<String, Object> props) throws Exception
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try {
                Report report = ReportService.get().getReportByEntityId(container, id);
                if (report != null)
                {
                    scope.ensureTransaction();

                    ViewCategory category = null;

                    // save the category information then the dataset information
                    if (props.containsKey(Property.category.name()))
                    {
                        String categoryName = StringUtils.trimToNull(String.valueOf(props.get(Property.category.name())));
                        if (categoryName != null)
                            category = ViewCategoryManager.getInstance().ensureViewCategory(container, user, categoryName);
                    }

                    if (category != null)
                        report.getDescriptor().setCategory(category);

                    if (props.containsKey(Property.description.name()))
                        report.getDescriptor().setReportDescription(StringUtils.trimToNull(String.valueOf(props.get(Property.description.name()))));

                    if (props.containsKey(Property.visible.name()))
                        report.getDescriptor().setHidden(!BooleanUtils.toBoolean(String.valueOf(props.get(Property.visible.name()))));

                    boolean shared = BooleanUtils.toBoolean(String.valueOf(props.get(Property.shared.name())));
                    if(shared)
                        report.getDescriptor().setOwner(null);
                    else
                        report.getDescriptor().setOwner(user.getUserId());

                    ReportService.get().saveReport(new DefaultContainerUser(container, user), report.getDescriptor().getReportKey(), report);

                    if (props.containsKey(Property.author.name()))
                        ReportPropsManager.get().setPropertyValue(id, container, Property.author.name(), props.get(Property.author.name()));
                    if (props.containsKey(Property.status.name()))
                        ReportPropsManager.get().setPropertyValue(id, container, Property.status.name(), props.get(Property.status.name()));
                    if (props.containsKey(Property.refreshDate.name()))
                        ReportPropsManager.get().setPropertyValue(id, container, Property.refreshDate.name(), props.get(Property.refreshDate.name()));

                    scope.commitTransaction();
                }
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }

    private static class DefaultContainerUser implements ContainerUser
    {
        private User _user;
        private Container _container;

        public DefaultContainerUser(Container container, User user)
        {
            _user = user;
            _container = container;
        }

        @Override
        public User getUser()
        {
            return _user;
        }

        @Override
        public Container getContainer()
        {
            return _container;
        }
    }
}
