/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
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
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.ImageStreamThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.study.StudySchema;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 2, 2012
 */
public class ReportViewProvider implements DataViewProvider
{
    public static final DataViewProvider.Type TYPE = new ProviderType("reports", "Report Views", true);

    public DataViewProvider.Type getType()
    {
        return TYPE;
    }

    @Override
    public boolean isVisible(Container container, User user)
    {
        return true;
    }

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
        Container c = context.getContainer();
        User user = context.getUser();

        ReportPropsManager.get().ensureProperty(c, user, "status", "Status", PropertyType.STRING);
        ReportPropsManager.get().ensureProperty(c, user, "author", "Author", PropertyType.INTEGER);
        ReportPropsManager.get().ensureProperty(c, user, "refreshDate", "RefreshDate", PropertyType.DATE_TIME);
        ReportPropsManager.get().ensureProperty(c, user, "thumbnailType", "ThumbnailType", PropertyType.STRING);
        ReportPropsManager.get().ensureProperty(c, user, "iconType", "IconType", PropertyType.STRING);
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

    private List<DataViewInfo> getViews(ViewContext context, @Nullable String schemaName, @Nullable String queryName, ReportUtil.ReportFilter filter)
    {
        Container c = context.getContainer();
        User user = context.getUser();
        boolean studyFolder = StudyService.get().getStudy(c) != null;

        if (filter == null)
            throw new IllegalArgumentException("ReportFilter cannot be null");

        String reportKey = null;

        if (schemaName != null && queryName != null)
            reportKey = ReportUtil.getReportKey(schemaName, queryName);

        List<DataViewInfo> views = new ArrayList<>();

        try
        {
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

                    DefaultViewInfo info = new DefaultViewInfo(TYPE, descriptor.getEntityId(), descriptor.getReportName(), descriptor.lookupContainer());

                    String query = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                    String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);

                    info.setSchemaName(schema);
                    info.setQueryName(query);

                    if (descriptor.getCategory() != null)
                        info.setCategory(descriptor.getCategory());
                    else
                        info.setCategory(ReportUtil.getDefaultCategory(c, schema, query));

                    info.setCreatedBy(createdBy);
                    info.setReportId(descriptor.getReportId().toString());
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

                    info.setRunTarget(r.getRunReportTarget());

                    info.setType(r.getTypeDescription());
                    info.setDescription(descriptor.getReportDescription());
                    info.setReadOnly(!r.canEdit(user, c));

                    String access;
                    if (descriptor.isModuleBased())
                    {
                        access = "public";
                        info.setShared(true);
                    }
                    else if (!descriptor.isShared())
                    {
                        access = "private";
                        info.setShared(false);
                    }
                    else if (!(descriptor instanceof ModuleRReportDescriptor) && !SecurityPolicyManager.getPolicy(descriptor, false).isEmpty())
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

                    // studies support dataset level report permissions
                    if (studyFolder)
                    {
                        ActionURL url = PageFlowUtil.urlProvider(StudyUrls.class).getManageReportPermissions(c).
                                                        addParameter(ReportDescriptor.Prop.reportId, r.getDescriptor().getReportId().toString());

                        URLHelper returnUrl = context.getActionURL().getReturnURL();
                        if (returnUrl != null)
                            url.addReturnURL(returnUrl);

                        info.setAccess(access, url);
                    }
                    else
                        info.setAccess(access);

                    info.setVisible(!descriptor.isHidden());

                    // This icon is the small icon -- not the same as thumbnail
                    String iconPath;
                    String iconType = (String)ReportPropsManager.get().getPropertyValue(r.getEntityId(), context.getContainer(), "iconType");

                    if(iconType != null && iconType.equals(EditInfo.ThumbnailType.CUSTOM.name()))
                        iconPath = PageFlowUtil.urlProvider(ReportUrls.class).urlIcon(c, r).toString();
                    else
                        iconPath = ReportService.get().getIconPath(r);

                    if (!StringUtils.isEmpty(iconPath))
                        info.setIcon(iconPath);

                    // see to-do below regarding static vs. dynamic thumbnail providers
                    info.setAllowCustomThumbnail(r instanceof DynamicThumbnailProvider);

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
                Property.viewName.name(),
                Property.description.name(),
                Property.category.name(),
                Property.visible.name(),
                Property.author.name(),
                Property.status.name(),
                Property.refreshDate.name(),
                Property.shared.name(),
                Property.customThumbnail.name(),
                Property.customThumbnailFileName.name(),
                Property.customIcon.name(),
                Property.customIconFileName.name()
        };

        private static final Actions[] _actions = {
                Actions.update,
                Actions.delete
        };

        @Override
        public String[] getEditableProperties(Container container, User user)
        {
            return _editableProperties;
        }

        @Override
        public void validateProperties(Container container, User user, String id, Map<String, Object> props) throws ValidationException
        {
            Report report = ReportService.get().getReportByEntityId(container, id);

            if (report != null)
            {
                List<ValidationError> errors = new ArrayList<>();

                ReportService.get().tryValidateReportPermissions(new DefaultContainerUser(container, user), report, errors);
                if (!errors.isEmpty())
                    throw new ValidationException(errors);
            }
        }

        @Override
        public void updateProperties(ViewContext context, String id, Map<String, Object> props) throws Exception
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            Report report = ReportService.get().getReportByEntityId(context.getContainer(), id);
            if (report != null)
            {
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    ReportDescriptor descriptor = report.getDescriptor();
                    ViewCategory category = null;

                    // save the category information then the dataset information
                    if (props.containsKey(Property.category.name()))
                    {
                        int categoryId = NumberUtils.toInt(String.valueOf(props.get(Property.category.name())));
                        category = ViewCategoryManager.getInstance().getCategory(categoryId);
                    }

                    descriptor.setCategory(category);

                    if (props.containsKey(Property.viewName.name()))
                        descriptor.setReportName(StringUtils.trimToNull(String.valueOf(props.get(Property.viewName.name()))));

                    if (props.containsKey(Property.description.name()))
                        descriptor.setReportDescription(StringUtils.trimToNull(String.valueOf(props.get(Property.description.name()))));

                    if (props.containsKey(Property.visible.name()))
                        descriptor.setHidden(!BooleanUtils.toBoolean(String.valueOf(props.get(Property.visible.name()))));

                    boolean isPrivate = !BooleanUtils.toBoolean(String.valueOf(props.get(Property.shared.name())));

                    if (isPrivate)
                    {
                        // If switching from shared to private then set owner back to original creator.
                        if (descriptor.isShared())
                        {
                            // Convey previous state to save code, otherwise admins will be denied the ability to unshare.
                            descriptor.setWasShared();
                            descriptor.setOwner(descriptor.getCreatedBy());
                        }
                    }
                    else
                    {
                        descriptor.setOwner(null);
                    }

                    if (props.containsKey(Property.author.name()))
                        descriptor.setAuthor(props.get(Property.author.name()));
                    if (props.containsKey(Property.status.name()))
                        descriptor.setStatus((String)props.get(Property.status.name()));
                    if (props.containsKey(Property.refreshDate.name()))
                        descriptor.setRefeshDate(props.get(Property.refreshDate.name()));

                    List<ValidationError> errors = new ArrayList<>();
                    ReportService.get().tryValidateReportPermissions(context, report, errors);
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);

                    ReportService.get().saveReport(new DefaultContainerUser(context.getContainer(), context.getUser()), descriptor.getReportKey(), report);

                    if (props.containsKey(Property.customThumbnail.name()))
                    {
                        // TODO: I don't like this... need to rethink static vs. dynamic providers. Reports that aren't dynamic providers should still allow custom thumbnails
                        if (report instanceof DynamicThumbnailProvider)
                        {
                            // custom thumbnail file provided by the user is stored in the properties map as an InputStream
                            InputStream is = (InputStream)props.get(Property.customThumbnail.name());
                            String filename = (String)props.get(Property.customThumbnailFileName.name());
                            String contentType = null != filename ? new MimeMap().getContentTypeFor(filename) : null;
                            DynamicThumbnailProvider wrapper = new ImageStreamThumbnailProvider((DynamicThumbnailProvider)report, is, contentType, ImageType.Large);

                            ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                            if (null != svc)
                            {
                                svc.replaceThumbnail(wrapper, ImageType.Large, context);
                                ReportPropsManager.get().setPropertyValue(report.getEntityId(), context.getContainer(), "thumbnailType", ThumbnailType.CUSTOM.name());
                            }
                        }
                    }

                    if (props.containsKey(Property.customIcon.name()))
                    {
                        if (report instanceof DynamicThumbnailProvider)
                        {
                            InputStream is = (InputStream)props.get(Property.customIcon.name());
                            String filename = (String)props.get(Property.customIconFileName);
                            String contentType = null != filename ? new MimeMap().getContentTypeFor(filename) : null;
                            DynamicThumbnailProvider wrapper = new ImageStreamThumbnailProvider((DynamicThumbnailProvider)report, is, contentType, ImageType.Small);

                            ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                            if (null != svc)
                            {
                                svc.replaceThumbnail(wrapper, ImageType.Small, context);
                                ReportPropsManager.get().setPropertyValue(report.getEntityId(), context.getContainer(), "iconType", ThumbnailType.CUSTOM.name());
                            }
                        }
                    }

                    transaction.commit();
                }
            }
        }

        @Override
        public Actions[] getAllowableActions(Container container, User user)
        {
            return _actions;
        }

        @Override
        public void deleteView(Container container, User user, String id) throws ValidationException
        {
            Report report = ReportService.get().getReportByEntityId(container, id);

            if (report != null)
            {
                List<ValidationError> errors = new ArrayList<>();
                try
                {
                    if (report.canDelete(user, container, errors))
                        ReportService.get().deleteReport(new DefaultContainerUser(container, user), report);
                    else
                        throw new ValidationException(errors);
                }
                catch (RuntimeSQLException e)
                {
                    throw new ValidationException(e.getMessage());
                }
            }
        }
    }
}
