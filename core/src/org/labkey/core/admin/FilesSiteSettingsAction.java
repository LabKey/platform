/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.labkey.api.admin.AdminUrls;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;

/**
 * User: klum
 * Date: Nov 24, 2009
 */

@RequiresSiteAdmin
public class FilesSiteSettingsAction extends AbstractFileSiteSettingsAction<FileSettingsForm>
{
    public FilesSiteSettingsAction()
    {
        super(FileSettingsForm.class);
    }

    public ModelAndView getView(FileSettingsForm form, boolean reshow, BindException errors) throws Exception
    {
        if (form.isUpgrade())
            getPageConfig().setTemplate(PageConfig.Template.Dialog);

        if (!reshow)
        {
            File root = _svc.getSiteDefaultRoot();

            if (root != null && root.exists())
                form.setRootPath(FileUtil.getAbsoluteCaseSensitiveFile(root).getAbsolutePath());
        }
        setHelpTopic("setRoots");
        return new JspView<>("/org/labkey/core/admin/view/filesSiteSettings.jsp", form, errors);
    }

    public ActionURL getSuccessURL(FileSettingsForm form)
    {
        if (form.isUpgrade())
            return PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL(true);
        else
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Configure File System Access", null);
    }

}
