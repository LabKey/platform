/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.thumbnail.ImageStreamThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailProvider;
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
        ReportPropsManager.get().ensureProperty(c, user, "thumbnailRevision", "ThumbnailRevision", PropertyType.INTEGER);
        ReportPropsManager.get().ensureProperty(c, user, "iconType", "IconType", PropertyType.STRING);
        ReportPropsManager.get().ensureProperty(c, user, "iconRevision", "IconRevision", PropertyType.INTEGER);
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

        if (filter == null)
            throw new IllegalArgumentException("ReportFilter cannot be null");

        String reportKey = null;

        if (schemaName != null && queryName != null)
            reportKey = ReportUtil.getReportKey(schemaName, queryName);

        List<DataViewInfo> views = new ArrayList<>();

        for (Report r : ReportUtil.getReportsIncludingInherited(c, user, reportKey))
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

                DefaultViewInfo info = new DefaultViewInfo(TYPE, descriptor.getEntityId(), descriptor.getReportName(), descriptor.isModuleBased() ? c : descriptor.lookupContainer());

                String query = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);

                info.setSchemaName(schema);
                info.setQueryName(query);

                ViewCategory category = descriptor.getCategory(c);

                if (category != null)
                    info.setCategory(category);
                else
                    info.setCategory(ReportUtil.getDefaultCategory(c, schema, query));

                info.setCreatedBy(createdBy);
                info.setReportId(descriptor.getReportId().toString());
                info.setCreated(descriptor.getCreated());
                info.setModifiedBy(modifiedBy);
                info.setAuthor(author);
                info.setModified(descriptor.getModified());
                info.setContentModified(descriptor.getContentModified());

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

                String access = descriptor.getAccess();
                info.setShared(!ReportDescriptor.REPORT_ACCESS_PRIVATE.equals(access));

                // report level permissions link for admins only
                ActionURL reportPermUrl = null;
                if (c.hasPermission(user, AdminPermission.class))
                {
                    reportPermUrl = PageFlowUtil.urlProvider(StudyUrls.class).getManageReportPermissions(c).
                        addParameter(ReportDescriptor.Prop.reportId, r.getDescriptor().getReportId().toString());

                    URLHelper returnUrl = context.getActionURL().getReturnURL();
                    if (returnUrl != null)
                        reportPermUrl.addReturnURL(returnUrl);
                }
                info.setAccess(access, reportPermUrl);

                info.setVisible(!descriptor.isHidden());

                // if a report doesn't have the 'showInDashboard' property set then default to true so that
                // reports that used to be shown are still shown.
                String showInDashboard = descriptor.getProperty(ReportDescriptor.Prop.showInDashboard);
                info.setShowInDashboard(showInDashboard == null || Boolean.valueOf(showInDashboard));

                // This is the small icon
                info.setIconUrl(ReportUtil.getIconUrl(c, r));
                String iconCls = ReportUtil.getIconCls(r);
                info.setDefaultIconCls(iconCls);

                // TODO: This is kind of an ugly check, should break out first part of getDynamicImageUrl() into separate existence check
                if(ReportUtil.getDynamicImageUrl(c, r, ImageType.Small) == null)
                {
                    info.setIconCls(iconCls);
                }


                // This is the thumbnail
                info.setAllowCustomThumbnail(true);
                info.setThumbnailUrl(ReportUtil.getThumbnailUrl(c, r));
                info.setDefaultThumbnailUrl(ReportUtil.getDefaultThumbnailUrl(c, r));

                info.setTags(ReportPropsManager.get().getProperties(descriptor.getEntityId(), c));

                info.setDisplayOrder(descriptor.getDisplayOrder());

                views.add(info);
            }
        }
        return views;
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
                Property.customIconFileName.name(),
                Property.deleteCustomThumbnail.name(),
                Property.deleteCustomIcon.name()
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
                    Integer categoryId = null;

                    // save the category information then the dataset information
                    if (props.containsKey(Property.category.name()))
                    {
                        // Validate that categoryId matches a category in this container
                        categoryId = NumberUtils.toInt(String.valueOf(props.get(Property.category.name())));
                        ViewCategory category = ViewCategoryManager.getInstance().getCategory(context.getContainer(), categoryId);
                        categoryId = null != category ? category.getRowId() : null;
                    }

                    descriptor.setCategoryId(categoryId);

                    if (props.containsKey(Property.viewName.name()))
                        descriptor.setReportName(StringUtils.trimToNull(String.valueOf(props.get(Property.viewName.name()))));

                    if (props.containsKey(Property.description.name()))
                        descriptor.setReportDescription(StringUtils.trimToNull(String.valueOf(props.get(Property.description.name()))));

                    if (props.containsKey(Property.visible.name()))
                        descriptor.setHidden(!BooleanUtils.toBoolean(String.valueOf(props.get(Property.visible.name()))));

                    // Note: Keep this code in sync with BaseReportAction.saveReport()
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
                    ThumbnailService svc1 = ServiceRegistry.get().getService(ThumbnailService.class);

                    boolean isDeleteThumbnail = props.containsKey(Property.deleteCustomThumbnail.name()) &&
                            Boolean.parseBoolean((String)props.get(Property.deleteCustomThumbnail.name()));
                    if (isDeleteThumbnail)
                    {
                        if (svc1 != null)
                        {
                            svc1.deleteThumbnail(report, ImageType.Large);
                        }
                        ReportPropsManager.get().setPropertyValue(report.getEntityId(), context.getContainer(), "thumbnailType", ThumbnailType.NONE.name());
                    }

                    if (!isDeleteThumbnail && props.containsKey(Property.customThumbnail.name()))
                    {
                        // custom thumbnail file provided by the user is stored in the properties map as an InputStream
                        InputStream is = (InputStream)props.get(Property.customThumbnail.name());
                        String filename = (String)props.get(Property.customThumbnailFileName.name());
                        String contentType = null != filename ? new MimeMap().getContentTypeFor(filename) : null;
                        ThumbnailProvider wrapper = new ImageStreamThumbnailProvider(report, is, contentType, ImageType.Large, false);


                        if (null != svc1)
                        {
                            // Note: afterSave() callback handles updating the imageType, thumbnailType, and image revision properties
                            svc1.replaceThumbnail(wrapper, ImageType.Large, ThumbnailType.CUSTOM, context);
                        }
                    }

                    boolean isDeleteIcon = props.containsKey(Property.deleteCustomIcon.name()) &&
                            Boolean.parseBoolean((String)props.get(Property.deleteCustomIcon.name()));
                    if (isDeleteIcon)
                    {
                        if (svc1 != null)
                        {
                            svc1.deleteThumbnail(report, ImageType.Small);
                        }
                        ReportPropsManager.get().setPropertyValue(report.getEntityId(), context.getContainer(), "iconType", ThumbnailType.NONE.name());
                    }

                    if (!isDeleteIcon && props.containsKey(Property.customIcon.name()))
                    {
                        InputStream is = (InputStream)props.get(Property.customIcon.name());
                        String filename = (String)props.get(Property.customIconFileName.name());
                        String contentType = null != filename ? new MimeMap().getContentTypeFor(filename) : null;
                        ThumbnailProvider wrapper = new ImageStreamThumbnailProvider(report, is, contentType, ImageType.Small, false);

                        if (null != svc1)
                        {
                            // Note: afterSave() callback handles updating the "iconType" property and image revision number
                            svc1.replaceThumbnail(wrapper, ImageType.Small, ThumbnailType.CUSTOM, context);
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
