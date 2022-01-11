/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Path;

@AdminConsoleAction
@RequiresPermission(AdminOperationsPermission.class)
public class UpdateFilePathsAction extends FormViewAction<UpdateFilePathsAction.UpdateFilePathsForm>
{
    public static class UpdateFilePathsForm
    {
        private String _originalPrefix;
        private String _newPrefix;

        public String getOriginalPrefix()
        {
            return _originalPrefix;
        }

        public void setOriginalPrefix(String originalPrefix)
        {
            _originalPrefix = originalPrefix;
        }

        public String getNewPrefix()
        {
            return _newPrefix;
        }

        public void setNewPrefix(String newPrefix)
        {
            _newPrefix = newPrefix;
        }
    }

    @Override
    public void validateCommand(UpdateFilePathsForm form, Errors errors)
    {
    }

    @Override
    public boolean handlePost(UpdateFilePathsForm form, BindException errors) throws Exception
    {
        Path source = null;
        try
        {
            if (StringUtils.isEmpty(form.getOriginalPrefix()))
            {
                errors.addError(new LabKeyError("No original prefix specified"));
            }
            else
            {
                source = Path.of(new URI(form.getOriginalPrefix()));
            }
        }
        catch (URISyntaxException | IllegalArgumentException e)
        {
            errors.addError(new LabKeyError("Invalid original prefix: " + form.getOriginalPrefix()));
        }
        Path target = null;
        try
        {
            if (StringUtils.isEmpty(form.getNewPrefix()))
            {
                errors.addError(new LabKeyError("No new prefix specified"));
            }
            else
            {
                target = Path.of(new URI(form.getNewPrefix()));
            }
        }
        catch (URISyntaxException | IllegalArgumentException e)
        {
            errors.addError(new LabKeyError("Invalid original prefix: " + form.getNewPrefix()));
        }

        if (source == null || target == null)
        {
            return false;
        }

        int rows = FileContentService.get().fireFileMoveEvent(source, target, getUser(), null);
        SiteSettingsAuditProvider.SiteSettingsAuditEvent event = new SiteSettingsAuditProvider.SiteSettingsAuditEvent(
                ContainerManager.getRoot().getId(),
                "Updated site-wide file paths from " + source + " to " + target);
        event.setChanges(rows + " row(s) updated in database tables");
        AuditLogService.get().addEvent(getUser(), event);

        return true;
    }

    @Override
    public ModelAndView getView(UpdateFilePathsForm form, boolean reshow, BindException errors) throws Exception
    {
        return new JspView<>("/org/labkey/core/admin/view/updateFilePaths.jsp", form, errors);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        root.addChild("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL());
        root.addChild("Files", PageFlowUtil.urlProvider(AdminUrls.class).getFilesSiteSettingsURL(false));
        root.addChild("Update File Paths");
    }

    @Override
    public URLHelper getSuccessURL(UpdateFilePathsForm updateFilePathsForm)
    {
        return PageFlowUtil.urlProvider(AdminUrls.class).getFilesSiteSettingsURL(false);
    }
}
