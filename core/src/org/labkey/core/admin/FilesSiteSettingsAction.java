/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.attachments.AttachmentDirectory;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Nov 24, 2009
 */

@RequiresSiteAdmin
public class FilesSiteSettingsAction extends FormViewAction<FilesSiteSettingsAction.FileSettingsForm>
{
    private static Logger _log = Logger.getLogger(FilesSiteSettingsAction.class);
    private FileContentService _svc = ServiceRegistry.get().getService(FileContentService.class);

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
            File root = _svc.getSiteDefaultRoot();

            if (root == null || !root.exists())
                root = getDefaultRoot();

            if (root != null && root.exists())
                form.setRootPath(root.getCanonicalPath());
        }
        return new JspView<FileSettingsForm>("/org/labkey/core/admin/view/filesSiteSettings.jsp", form, errors);
    }

    public boolean handlePost(FileSettingsForm form, BindException errors) throws Exception
    {
        _svc.setSiteDefaultRoot(new File(form.getRootPath()));

        if (form.isUpgrade())
        {
            upgradeExistingFileSets();
        }
        return true;
    }

    private void upgradeExistingFileSets()
    {
        _log.info("Upgrading existing file sets to @files");

        Map<String, Container> fileSets = new HashMap<String, Container>();
        findExistingFileSets(ContainerManager.getRoot(), fileSets);

        for (Map.Entry<String, Container> entry : fileSets.entrySet())
        {
            File dir = new File(entry.getKey());
            if (dir.exists() && dir.isDirectory())
            {
                if (dir.getParent() != null && !dir.getParent().equals(_svc.getFolderName(FileContentService.ContentType.files)))
                {
                    File dest = new File(dir, _svc.getFolderName(FileContentService.ContentType.files));

                    try {
                        for (File child : dir.listFiles())
                        {
                            // don't move server managed folders
                            if (allowCopy(child, entry.getValue()))
                            {
                                if (!dest.exists())
                                    dest.mkdirs();

                                _log.info("moving " + child.getPath() + " to " + dest.getPath());
                                FileUtil.copyBranch(child, dest);
                                if (child.isDirectory())
                                    FileUtil.deleteDir(child);
                                else
                                    child.delete();
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        _log.error("error occured upgrading existing file sets", e);
                    }
                }
            }
        }
    }

    private boolean allowCopy(File child, Container c)
    {
        String name = child.getName();

        if (_svc.getFolderName(FileContentService.ContentType.files).equals(name))
            return false;

        if (child.isDirectory() && !child.getName().equals(".deleted"))
            return false;
/*
        // don't copy server managed folders
        if (child.isDirectory() && c.hasChild(name))
        {
            return false;
        }
*/
        return true;
    }

    private void findExistingFileSets(Container c, Map<String, Container> fileSets)
    {
        if (c == null) return;

        try {
            AttachmentDirectory root = _svc.getMappedAttachmentDirectory(c, false);
            if (root != null)
            {
                addTo(root.getFileSystemDirectory(), c, fileSets);
            }

/*
            for (AttachmentDirectory fileSet : getRegisteredDirectories(c))
            {
                addTo(fileSet.getFileSystemDirectory(), c, fileSets);
            }

            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);
            if (pipeRoot != null)
            {
                addTo(pipeRoot.getRootPath(), c, fileSets);
            }
*/

            for (Container child : c.getChildren())
                findExistingFileSets(child, fileSets);
        }
        catch (Exception e)
        {
            _log.error("error occured upgrading existing file sets", e);
        }
    }

    private void addTo(File dir, Container c, Map<String, Container> fileSets)
    {
        try {
            if (dir.exists())
            {
                if (_svc.getFolderName(FileContentService.ContentType.files).equals(dir.getName()))
                {
                    dir = dir.getParentFile();
                    if (dir == null || !dir.exists())
                        return;
                }
                String path = dir.getCanonicalPath();

                if (!fileSets.containsKey(path))
                    fileSets.put(path, c);
            }
        }
        catch (IOException e)
        {
            _log.error("error occurred getting existing filesets", e);
        }
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
        return root.addChild("Configure File System Access");
    }

    private File getDefaultRoot()
    {
        File explodedPath = ModuleLoader.getInstance().getCoreModule().getExplodedPath();

        File root = explodedPath.getParentFile();
        if (root != null)
        {
            if (root.getParentFile() != null)
                root = root.getParentFile();
        }
        File defaultRoot = new File(root, "files");
        if (!defaultRoot.exists())
            defaultRoot.mkdirs();

        return defaultRoot;
    }

    public static class FileSettingsForm
    {
        private String _rootPath;
        private boolean _upgrade;

        public String getRootPath()
        {
            return _rootPath;
        }

        public void setRootPath(String rootPath)
        {
            _rootPath = rootPath;
        }

        public boolean isUpgrade()
        {
            return _upgrade;
        }

        public void setUpgrade(boolean upgrade)
        {
            _upgrade = upgrade;
        }
    }
}
