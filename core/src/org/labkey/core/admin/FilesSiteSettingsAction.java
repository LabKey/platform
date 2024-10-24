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

import org.junit.Test;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
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

@AdminConsoleAction
@RequiresPermission(AdminOperationsPermission.class)
public class FilesSiteSettingsAction extends AbstractFileSiteSettingsAction<FileSettingsForm>
{
    public FilesSiteSettingsAction()
    {
        super(FileSettingsForm.class);
    }

    @Override
    public ModelAndView getView(FileSettingsForm form, boolean reshow, BindException errors) throws Exception
    {
        if (form.isUpgrade())
            getPageConfig().setTemplate(PageConfig.Template.Dialog);

        if (!reshow)
        {
            File root = _svc.getSiteDefaultRoot();

            if (root.exists())
                form.setRootPath(FileUtil.getAbsoluteCaseSensitiveFile(root).getAbsolutePath());

            form.setWebfilesEnabled(AppProps.getInstance().isWebfilesRootEnabled());
            form.setFileUploadDisabled(AppProps.getInstance().isFileUploadDisabled());
            form.setInvalidUploadBlocked(AppProps.getInstance().isInvalidFilenameUploadBlocked());
            form.setInvalidFilenameBlocked(AppProps.getInstance().isInvalidFilenameBlocked());
        }
        setHelpTopic("setRoots");
        return new JspView<>("/org/labkey/core/admin/view/filesSiteSettings.jsp", form, errors);
    }

    @Override
    public ActionURL getSuccessURL(FileSettingsForm form)
    {
        if (form.isUpgrade())
            return PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL(true);
        else
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        PageFlowUtil.urlProvider(AdminUrls.class).addAdminNavTrail(root, "Configure File System Access", getClass(), getContainer());
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        @Test
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            // @AdminConsoleAction
            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(ContainerManager.getRoot(), user,
                new FilesSiteSettingsAction()
            );
        }
    }
}
