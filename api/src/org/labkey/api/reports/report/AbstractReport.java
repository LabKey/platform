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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.views.DataViewProvider.EditInfo.ThumbnailType;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.permissions.EditSharedReportPermission;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: migra
 * Date: Mar 6, 2006
 * Time: 7:55:56 PM
 */
public abstract class AbstractReport implements Report, Cloneable // TODO: Remove this and switch to a builder pattern.
{
    private ReportDescriptor _descriptor;

    public String getDescriptorType()
    {
        return ReportDescriptor.TYPE;
    }

    public ReportIdentifier getReportId()
    {
        return getDescriptor().getReportId();
    }

    public void setReportId(ReportIdentifier reportId)
    {
        getDescriptor().setReportId(reportId);
    }

    public void beforeSave(ContainerUser context)
    {
        if (getDescriptor() != null && hasContentModified(context))
            getDescriptor().setContentModified();
    }

    @Override  // TODO: Temporary workaround for current save report pattern, which pulls reports from the cache, mutates them, and saves. Should switch to a builder pattern.
    public Report clone()
    {
        try
        {
            Report clone = (Report)super.clone();
            clone.setDescriptor(clone.getDescriptor().clone());

            return clone;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasContentModified(ContainerUser context)
    {
        return false;
    }

    protected boolean hasDescriptorPropertyChanged(String descriptorPropName)
    {
        // Content modified if change to the specified descriptor property
        String newPropStr = getDescriptor().getProperty(descriptorPropName);

        String origPropStr = null;
        if (getReportId() != null)
        {
            Report origReport = ReportService.get().getReport(ContainerManager.getForId(getContainerId()), getReportId().getRowId());
            origPropStr = origReport != null  ? origReport.getDescriptor().getProperty(descriptorPropName) : null;
        }

        return newPropStr != null && !newPropStr.equals(origPropStr);
    }

    // Delete attachments and thumbnails on delete
    @Override
    public void beforeDelete(ContainerUser context)
    {
        deleteAttachments();
    }

    protected void deleteAttachments()
    {
        if (null == getEntityId())
            return;

        AttachmentService.get().deleteAttachments(this);
    }

    public ReportDescriptor getDescriptor()
    {
        if (_descriptor == null)
        {
            _descriptor = ReportService.get().createDescriptorInstance(getDescriptorType());
            _descriptor.setReportType(getType());
        }
        return _descriptor;
    }

    public void setDescriptor(ReportDescriptor descriptor)
    {
        _descriptor = descriptor;
    }

    @Override
    public ActionURL getRunReportURL(ViewContext context)
    {
        return ReportUtil.getRunReportURL(context, this, true);
    }

    @Override
    public String getRunReportTarget()
    {
        return null;
    }

    @Nullable
    public ActionURL getEditReportURL(ViewContext context)
    {
        return null;
    }

    // Callers should pass in the "after save" redirect location; report might not be able to figure this out
    // (e.g., when manage views call this method, context.getActionURL() is a JSON API action)
    public @Nullable ActionURL getEditReportURL(ViewContext context, ActionURL returnURL)
    {
        ActionURL url = getEditReportURL(context);
        return null != url ? url.addReturnURL(returnURL) : null;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return new HtmlView("No Data view available for this report");
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return renderReport(context);
    }

    public ActionURL getDownloadDataURL(ViewContext context)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlDownloadData(context.getContainer());

        for (Pair<String, String> param : context.getActionURL().getParameters())
        {
            url.replaceParameter(param.getKey(), param.getValue());
        }
        url.replaceParameter(ReportDescriptor.Prop.reportType.toString(), getDescriptor().getReportType());
        url.replaceParameter(ReportDescriptor.Prop.schemaName, getDescriptor().getProperty(ReportDescriptor.Prop.schemaName));
        url.replaceParameter(ReportDescriptor.Prop.queryName, getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
        url.replaceParameter(ReportDescriptor.Prop.viewName, getDescriptor().getProperty(ReportDescriptor.Prop.viewName));
        url.replaceParameter(ReportDescriptor.Prop.dataRegionName, getDescriptor().getProperty(ReportDescriptor.Prop.dataRegionName));

        return url;
    }

    public void clearCache()
    {
    }

    @Override
    public Map<String, Object> serialize(Container container, User user)
    {
        Map<String, Object> props = new HashMap<>();
        ReportDescriptor descriptor = getDescriptor();

        props.put("name", descriptor.getReportName());
        props.put("description", descriptor.getReportDescription());
        props.put("schemaName", descriptor.getProperty(ReportDescriptor.Prop.schemaName));
        props.put("queryName", descriptor.getProperty(ReportDescriptor.Prop.queryName));
        props.put("viewName", descriptor.getProperty(ReportDescriptor.Prop.viewName));

        props.put("editable", canEdit(user, container));
        props.put("public", descriptor.isShared());

        // the rest of the properties
        props.put("properties", descriptor.getProperties());

        return props;
    }

    public void serialize(ImportContext context, VirtualFile dir, String filename) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
            descriptor.serialize(context, dir, filename);
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    public void serializeToFolder(ImportContext context, VirtualFile dir) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
        {
            ReportNameContext rec = (ReportNameContext) context.getContext(ReportNameContext.class);
            String serializedName = rec.getSerializedName();

            serializeThumbnail(serializedName, dir, new ReportThumbnailLarge(context.getContainer(), this));
            serializeThumbnail(serializedName, dir, new ReportThumbnailSmall(context.getContainer(), this));
            serialize(context, dir, String.format("%s.report.xml", serializedName));
        }
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    @Override
    public void afterImport(Container container, User user)
    {
    }

    @Override
    public void afterSave(Container container, User user, VirtualFile root)
    {
        if (root != null)
        {
            deserializeThumbnail(user, root, new ReportThumbnailLarge(container, this));
            deserializeThumbnail(user, root, new ReportThumbnailSmall(container, this));
        }
    }

    public void afterDeserializeFromFile(File reportFile) throws IOException
    {
    }

    @Override
    public String getEntityId()
    {
        return getDescriptor().getEntityId();
    }

    @Override
    public String getContainerId()
    {
        return getDescriptor().getContainerId();
    }

    @NotNull
    @Override
    public AttachmentType getAttachmentType()
    {
        return ReportType.get();
    }

    @Override
    public String getStaticThumbnailPath()
    {
        return "/report/report.jpg";
    }

    @Override
    public String getThumbnailCacheKey()
    {
        return "Reports:" + getReportId();
    }

    @Override
    public boolean supportsDynamicThumbnail()
    {
        return false;
    }

    @Nullable
    @Override
    public Thumbnail generateThumbnail(@Nullable ViewContext context)
    {
        throw new IllegalStateException("Should call supportsDynamicThumbnail() before calling this");
    }

    @Override
    public void afterThumbnailSave(ImageType imageType, ThumbnailType thumbnailType)
    {
        Container c = ContainerManager.getForId(getContainerId());

        if (null != c)
        {
            try
            {
                String entityId = getEntityId();
                String typePropertyName = imageType.getPropertyNamePrefix() + "Type";
                ReportPropsManager.get().setPropertyValue(entityId, c, typePropertyName, thumbnailType.name());

                String revisionPropertyName = imageType.getPropertyNamePrefix() + "Revision";
                Double revision = (Double) ReportPropsManager.get().getPropertyValue(entityId, c, revisionPropertyName);
                ReportPropsManager.get().setPropertyValue(entityId, c, revisionPropertyName, null == revision ? 1 : revision.intValue() + 1);
            }
            catch (ValidationException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    @Override
    public void afterThumbnailDelete(ImageType imageType)
    {
        Container c = ContainerManager.getForId(getContainerId());

        if (null != c)
        {
            try
            {
                String propertyName = imageType.getPropertyNamePrefix() + "Type";
                ReportPropsManager.get().setPropertyValue(getEntityId(), c, propertyName, ThumbnailType.NONE.name());

                // Note: we don't modify or delete "revision" property on delete; we want the sequence to continue if
                // another thumbnail is uploaded or generated, so clients don't render an old images.
            }
            catch (ValidationException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }


    @Override
    public boolean canEdit(User user, Container container, List<ValidationError> errors)
    {
        if (getDescriptor().isInherited(container))
        {
            errors.add(new SimpleValidationError("An inherited report can only be edited from its source folder."));
            return false;
        }

        // public, custom, or private report
        if (isPrivate() || getDescriptor().hasCustomAccess())
        {
            if (!isOwner(user))
                errors.add(new SimpleValidationError("You must be the owner of a private or custom report in order to edit it."));
        }
        else if (!isOwner(user) && !container.hasPermission(user, EditSharedReportPermission.class))
        {
            errors.add(new SimpleValidationError("You must be in the Editor role to update a shared report."));
        }

        return errors.isEmpty();
    }

    public boolean canEdit(User user, Container container)
    {
        return !getDescriptor().isModuleBased() && canEdit(user, container, new ArrayList<>());

    }

    public boolean canShare(User user, Container container, List<ValidationError> errors)
    {
        if (getDescriptor().isInherited(container))
        {
            errors.add(new SimpleValidationError("An inherited report can only be modified from its source folder."));
            return false;
        }

        if (isOwner(user))
        {
            if(!container.hasPermission(user, ShareReportPermission.class))
                errors.add(new SimpleValidationError("You must be in the Author role to share your report."));
        }
        else
        {
            if (isPrivate() || getDescriptor().hasCustomAccess())
                errors.add(new SimpleValidationError("You must be the owner to share a private or custom report."));
            else if(!container.hasPermission(user, EditSharedReportPermission.class))
                errors.add(new SimpleValidationError("You must be in the Editor role to share a public report."));
        }
        return errors.isEmpty();
    }

    public boolean canShare(User user, Container container)
    {
        return canShare(user, container, new ArrayList<>());
    }

    @Override
    public boolean allowShareButton(User user, Container container)
    {
        // See RReport, currently only allowing ShareReportAction for that report type
        return false;
    }

    public boolean canDelete(User user, Container container)
    {
        return canDelete(user, container, new ArrayList<>());
    }

    public boolean canDelete(User user, Container container, List<ValidationError> errors)
    {
        if (getDescriptor().isInherited(container))
        {
            errors.add(new SimpleValidationError("An inherited report can only be deleted from its source folder."));
            return false;
        }

        if (!isOwner(user))
        {
            // public or private report
            if (isPrivate())
                errors.add(new SimpleValidationError("You must be the owner of a private report in order to delete it."));
            else if (!container.hasPermission(user, DeletePermission.class))
                errors.add(new SimpleValidationError("You must be in the Editor role to delete a public report."));
        }
        return errors.isEmpty();
    }

    protected String getAttachmentDir()
    {
        return ReportUtil.getSerializedName(getDescriptor());
    }

    protected void serializeThumbnail(String serializeName, VirtualFile dir, ReportThumbnail thumbnail) throws IOException
    {
        if (thumbnail.shouldSerialize())
        {
            Attachment attachment = AttachmentService.get().getAttachment(this, thumbnail.getFilename());
            serializeAttachment(serializeName, dir, attachment);

            // if we had an auto-generated attachment then update the thumnailType property
            // to AUTO for exporting if it wasn't already set.  On import, we'll look to see if it is set to know
            // to read in the file.  Note that we don't need to do this for custom icons which is why ReportThumbnailSmall
            // doesn't implement this function
            if (attachment != null)
                thumbnail.setAutoThumbnailType();
        }
    }

    protected void serializeAttachment(String serializeName, VirtualFile parentDir, Attachment attachment) throws IOException
    {
        // for attachment reports and thumbnails, write the attachment to a subdirectory that is the same as the report name to avoid collisions
        VirtualFile reportDir = parentDir.getDir(serializeName);
        if (attachment != null && attachment.getName() != null)
        {
            try (InputStream is = AttachmentService.get().getInputStream(this, attachment.getName()); OutputStream os = reportDir.getOutputStream(attachment.getName()))
            {
                FileUtil.copyData(is, os);
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Attachment report file not found: " + attachment.getName());
            }
        }
    }

    protected void deserializeThumbnail(User user, VirtualFile root, ReportThumbnail thumbnail)
    {
        if (thumbnail.shouldDeserialize())
            deserializeAttachment(user, root, thumbnail.getFilename());
    }

    protected void deserializeAttachment(User user, VirtualFile root, String attachment)
    {
        if (attachment != null)
        {
            String attachmentDir = getAttachmentDir();
            VirtualFile reportDir = root.getDir(attachmentDir);
            try
            {
                InputStream is = reportDir.getInputStream(attachment);
                // for older exported folders, thumbnail attachment files may not exist so be sure to check
                // for that case
                if (is != null)
                {
                    AttachmentFile attachmentFile = new InputStreamAttachmentFile(is, attachment);
                    AttachmentService.get().addAttachments(this, new ArrayList<>(Collections.singleton(attachmentFile)), user);
                }
            }
            catch (Exception e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
    }

    /**
     * Is this a new unsaved report
     */
    protected boolean isNew()
    {
        return getDescriptor().isNew();
    }

    /**
     * Is this a private report.
     * Consider : moving away from the owner field to determine public/private and replacing with a simple
     * boolean check
     */
    protected boolean isPrivate()
    {
        return isNew() || !getDescriptor().isShared();
    }

    /**
     * Did the current user create this report? This is our only real concept of "owner" with a report (the "owner" descriptor property is deprecated).
     */
    protected boolean isOwner(UserPrincipal user)
    {
        return isNew() || (getDescriptor().getCreatedBy() != 0 && (getDescriptor().getCreatedBy() == user.getUserId()));
    }

    /**
     * Tests whether the specified query view is valid.
     */
    public final void validateQueryView(QueryView view) throws ValidationException
    {
        if (view != null)
        {
            QueryDefinition def = view.getQueryDef();
            if (def != null)
            {
                List<QueryException> errors = new ArrayList<>();
                TableInfo table = def.getTable(errors, false);

                if (!errors.isEmpty())
                {
                    StringBuilder sb = new StringBuilder();
                    String delim = "";

                    for (QueryException error : errors)
                    {
                        sb.append(delim).append(error.getMessage());
                        delim = "\n";
                    }
                    throw new ValidationException("Unable to get table or query: " + sb.toString());
                }

                if (table == null)
                    throw new ValidationException("Table or query not found: " + view.getSettings().getQueryName());
            }
            else
                throw new ValidationException("Unable to get a query definition from table or query: " + view.getSettings().getQueryName());
        }
    }

    @Override
    public final boolean hasPermission(@NotNull UserPrincipal user, @NotNull Container c, @NotNull Class<? extends Permission> perm)
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.isModuleBased())
        {
            return ReadPermission.class == perm;
        }

        // only inherited reports can be viewed outside of its original folder
        if (!c.getId().equals(descriptor.getContainerId()))
        {
            if (!ReportUtil.isReportInherited(c, this))
                return false;
        }

        if (c.hasPermission(user, perm))
        {
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(descriptor, false);
            if (!policy.isEmpty())
            {
                // Issue 32103: even if custom SecurityPolicy present, the original creator/owner always has permissions
                return policy.hasPermission(user, perm) || isOwner(user);
            }
            else
            {
                return !isPrivate() || isOwner(user);
            }
        }
        return false;
    }
}
