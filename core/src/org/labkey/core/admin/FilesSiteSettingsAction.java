/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Nov 24, 2009
 */

@RequiresSiteAdmin
public class FilesSiteSettingsAction extends FormViewAction<FilesSiteSettingsAction.FileSettingsForm>
{
    public void validateCommand(FileSettingsForm form, Errors errors)
    {
        String webRoot = StringUtils.trimToNull(form.getRootPath());
        if (webRoot != null)
        {
            File f = new File(webRoot);
            if (!f.exists() || !f.isDirectory())
            {
                errors.reject(SpringActionController.ERROR_MSG, "Web root '" + webRoot + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
            }
        }
        else
            errors.reject(SpringActionController.ERROR_MSG, "The site file root cannot be blank.");
    }

    public ModelAndView getView(FileSettingsForm form, boolean reshow, BindException errors) throws Exception
    {
        if (!reshow)
        {
            File root = AppProps.getInstance().getFileSystemRoot();
            if (root != null && root.exists())
                form.setRootPath(root.getCanonicalPath());
        }
        return new JspView<FileSettingsForm>("/org/labkey/core/admin/view/filesSiteSettings.jsp", form, errors);
    }

    public boolean handlePost(FileSettingsForm form, BindException errors) throws Exception
    {
        WriteableAppProps props = AppProps.getWriteableInstance();

        props.setFileSystemRoot(form.getRootPath());
        props.save();
        props.writeAuditLogEvent(getViewContext().getUser(), props.getOldProperties());
        return true;
    }

    public ActionURL getSuccessURL(FileSettingsForm form)
    {
        return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild("Configure File System Access");
    }

    public static class FileSettingsForm
    {
        private String _rootPath;

        public String getRootPath()
        {
            return _rootPath;
        }

        public void setRootPath(String rootPath)
        {
            _rootPath = rootPath;
        }
    }
}
