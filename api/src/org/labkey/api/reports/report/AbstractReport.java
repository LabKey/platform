/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.permissions.EditSharedReportPermission;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: migra
 * Date: Mar 6, 2006
 * Time: 7:55:56 PM
 */
public abstract class AbstractReport implements Report
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

    public void beforeSave(ContainerUser context){}

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

    public ActionURL getRunReportURL(ViewContext context)
    {
        return ReportUtil.getRunReportURL(context, this);
    }

    @Override
    public String getRunReportTarget()
    {
        return null;
    }

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
            String filename = String.format("%s.%s.report.xml", descriptor.getReportName() != null ? descriptor.getReportName() : descriptor.getReportType(), descriptor.getReportId());
            serialize(context, dir, filename);
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

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = AbstractReport.class.getResourceAsStream("report.jpg");
        return new Thumbnail(is, "image/jpeg");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:ReportStatic";
    }

    public boolean canEdit(User user, Container container, List<ValidationError> errors)
    {
        if (getDescriptor().isInherited(container))
        {
            errors.add(new SimpleValidationError("An inherited report can only be edited from it's source folder."));
            return false;
        }

        // public or private report
        if (isPrivate())
        {
            if (!isOwner(user))
                errors.add(new SimpleValidationError("You must be the owner of a private report in order to edit it."));
        }
        else if (!isOwner(user) && !container.hasPermission(user, EditSharedReportPermission.class))
        {
            errors.add(new SimpleValidationError("You must be in the Editor role to update a shared report."));
        }

/*
        if (container.hasPermission(user, AdminPermission.class))
            return true;
        if (getCreatedBy() != 0)
            return (getCreatedBy() == user.getUserId());
        return false;
*/
        return errors.isEmpty();
    }

    public boolean canEdit(User user, Container container)
    {
        return canEdit(user, container, new ArrayList<ValidationError>());
    }

    public boolean canShare(User user, Container container, List<ValidationError> errors)
    {
        if (getDescriptor().isInherited(container))
        {
            errors.add(new SimpleValidationError("An inherited report can only be modified from it's source folder."));
            return false;
        }

        if (isOwner(user))
        {
            if(!container.hasPermission(user, ShareReportPermission.class))
                errors.add(new SimpleValidationError("You must be in the Author role to share your report."));
        }
        else
        {
            if(!container.hasPermission(user, EditSharedReportPermission.class))
                errors.add(new SimpleValidationError("You must be in the Editor role to share a public report."));
        }
        return errors.isEmpty();
    }

    public boolean canShare(User user, Container container)
    {
        return canShare(user, container, new ArrayList<ValidationError>());
    }

    public boolean canDelete(User user, Container container)
    {
        return canDelete(user, container, new ArrayList<ValidationError>());
    }

    public boolean canDelete(User user, Container container, List<ValidationError> errors)
    {
        if (getDescriptor().isInherited(container))
        {
            errors.add(new SimpleValidationError("An inherited report can only be deleted from it's source folder."));
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
        return isNew() || getDescriptor().getOwner() != null;
    }
    
    protected boolean isOwner(User user)
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
                List<QueryException> errors = new ArrayList<QueryException>();
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
}
