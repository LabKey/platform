/*
 * Copyright (c) 2011-2026 LabKey Corporation
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
package org.labkey.core.admin;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.RandomStartupProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Sep 26, 2011
 */
public abstract class AbstractFileSiteSettingsAction<FormType extends FileSettingsForm> extends FormViewAction<FormType>
{
    protected FileContentService _svc = FileContentService.get();

    public AbstractFileSiteSettingsAction(Class<FormType> commandClass)
    {
        super(commandClass);
    }

    @Override
    public void validateCommand(FormType form, Errors errors)
    {
        String webRoot = StringUtils.trimToNull(form.getRootPath());
        if (webRoot != null)
        {
            File f = new File(webRoot);

            try
            {
                boolean isNewRoot = isNewRoot(_svc.getSiteDefaultRoot(), f);
                if (!NetworkDrive.exists(f) || !f.isDirectory())
                {
                    errors.reject(SpringActionController.ERROR_MSG, "File root '" + webRoot + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                }
                else if (isNewRoot)
                {
                    // if this is a new root, make sure it is empty
                    String[] children = f.list();

                    if (children != null && children.length > 0)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "File root '" + webRoot + "' is not empty and cannot be used because files under the current site-level root must be moved to this new root. " +
                                "Either specify a different, non-existing root, or remove the files under the specified directory.");
                    }
                }
            }
            catch (IOException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, "The specified file root is invalid.");
            }
        }
        else
        {
            // this may have been set by startup properties
            boolean isFileSystemRootSet = null != AppProps.getInstance().getFileSystemRoot() && AppProps.getInstance().getFileSystemRoot().isDirectory();
            if (!isFileSystemRootSet)
                errors.reject(SpringActionController.ERROR_MSG, "The site file root cannot be blank.");
        }
    }

    private boolean isNewRoot(File prev, File current) throws IOException
    {
        String prevRoot = prev != null ? prev.getCanonicalPath() : "";
        return !current.getCanonicalPath().equals(prevRoot);
    }

    @Override
    public boolean handlePost(FormType form, BindException errors) throws Exception
    {
        File prev = _svc.getSiteDefaultRoot();
        if (null != form.getRootPath())
            _svc.setSiteDefaultRoot(FileUtil.getAbsoluteCaseSensitiveFile(new File(form.getRootPath())), getUser());
        _svc.setWebfilesEnabled(form.isWebfilesEnabled(), getUser());

        if (isNewRoot(prev, _svc.getSiteDefaultRoot()))
        {
            _svc.moveFileRoot(prev, _svc.getSiteDefaultRoot(), getUser(), getContainer());
        }

        saveFileUploadDisabledSetting(form, getUser());

        return true;
    }

    private String getDisableFileUploadDiff(String title, boolean before, boolean after)
    {
        return "<table><tr><td class='labkey-form-label'>" + PageFlowUtil.filter(title) + "</td><td>" + PageFlowUtil.filter(before) +
                "&nbsp;&raquo;&nbsp;" +
                PageFlowUtil.filter(after) +
                "</td></tr></table>";
    }

    private void saveFileUploadDisabledSetting(FormType form, User user)
    {
        SiteSettingsAuditProvider.SiteSettingsAuditEvent event = new SiteSettingsAuditProvider.SiteSettingsAuditEvent(ContainerManager.getRoot(), "The setting for disable file upload was changed (see details).");

        WriteableAppProps props = AppProps.getWriteableInstance();
        boolean hasChange = false;
        if (PremiumService.get().isDisableFileUploadSupported() && form.isFileUploadDisabled() != PremiumService.get().isFileUploadDisabled())
        {
            hasChange = true;
            props.setFileUploadDisabled(form.isFileUploadDisabled());
            event.setChanges(getDisableFileUploadDiff(RandomStartupProperties.fileUploadDisabled.getDescription(), PremiumService.get().isFileUploadDisabled(), form.isFileUploadDisabled()));
        }
        if (form.isInvalidUploadBlocked() != AppProps.getInstance().isInvalidFilenameUploadBlocked())
        {
            hasChange = true;
            props.setInvalidFilenameUploadBlocked(form.isInvalidUploadBlocked());
            event.setChanges(getDisableFileUploadDiff(RandomStartupProperties.invalidFilenameUploadBlocked.getDescription(), AppProps.getInstance().isInvalidFilenameUploadBlocked(), form.isInvalidUploadBlocked()), true);
        }
        if (form.isInvalidFilenameBlocked() != AppProps.getInstance().isInvalidFilenameBlocked())
        {
            hasChange = true;
            props.setInvalidFilenameBlocked(form.isInvalidFilenameBlocked());
            event.setChanges(getDisableFileUploadDiff(RandomStartupProperties.invalidFilenameBlocked.getDescription(), AppProps.getInstance().isInvalidFilenameBlocked(), form.isInvalidFilenameBlocked()), true);
        }
        if (hasChange)
        {
            AuditLogService.get().addEvent(getUser(), event);
            props.save(user);
        }
    }
}
