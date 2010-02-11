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
import org.labkey.api.view.template.PageConfig;
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

            try {
                boolean isNewRoot = isNewRoot(_svc.getSiteDefaultRoot(), f);

                if (!f.exists() && !f.isDirectory())
                {
                    errors.reject(SpringActionController.ERROR_MSG, "File Root '" + webRoot + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                }
                else if (isNewRoot)
                {
                    // if this is a new root, make sure it is empty
                    String[] children = f.list();

                    if (children != null && children.length > 0)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "File Root '" + webRoot + "' is not empty and cannot be used because files under the current site-level root must be moved to this new root. " +
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
            errors.reject(SpringActionController.ERROR_MSG, "The site file root cannot be blank.");
    }

    public ModelAndView getView(FileSettingsForm form, boolean reshow, BindException errors) throws Exception
    {
        if (form.isUpgrade())
            getPageConfig().setTemplate(PageConfig.Template.Dialog);

        if (!reshow)
        {
            File root = _svc.getSiteDefaultRoot();

            if (root != null && root.exists())
                form.setRootPath(root.getCanonicalPath());
        }
        return new JspView<FileSettingsForm>("/org/labkey/core/admin/view/filesSiteSettings.jsp", form, errors);
    }

    private boolean isNewRoot(File prev, File current) throws IOException
    {
        String prevRoot = prev != null ? prev.getCanonicalPath() : "";
        return !current.getCanonicalPath().equals(prevRoot);
    }

    public boolean handlePost(FileSettingsForm form, BindException errors) throws Exception
    {
        File prev = _svc.getSiteDefaultRoot();
        _svc.setSiteDefaultRoot(new File(form.getRootPath()));

        if (form.isUpgrade())
        {
            upgradeExistingFileSets();
        }
        else if (isNewRoot(prev, _svc.getSiteDefaultRoot()))
        {
            moveSiteRoot(prev, _svc.getSiteDefaultRoot());
        }
        return true;
    }

    private void moveSiteRoot(File prev, File dest)
    {
        try {
            _log.info("moving " + prev.getPath() + " to " + dest.getPath());
            boolean doRename = true;

            // attempt to rename, if that fails (try the more expensive copy)
            if (dest.exists())
                doRename = dest.delete();

            if (doRename && !prev.renameTo(dest))
            {
                File parentDir = dest.getParentFile();

                if (parentDir != null && parentDir.exists())
                {
                    FileUtil.copyBranch(prev, parentDir);
                    FileUtil.deleteDir(prev);
                }
            }
        }
        catch (IOException e)
        {
            _log.error("error occured moving the site-level file root", e);
        }

    }

    private void upgradeExistingFileSets()
    {
        _log.info("Upgrading existing file roots to @files");

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
                                _log.info("moving " + child.getPath() + " to " + dest.getPath());

                                // attempt a rename, if that fails try the more expensive file by file copy
                                File newChild = new File(dest, child.getName());
                                if (!child.renameTo(newChild))
                                {
                                    if (!dest.exists())
                                        dest.mkdirs();

                                    FileUtil.copyBranch(child, dest);
                                    if (child.isDirectory())
                                        FileUtil.deleteDir(child);
                                    else
                                        child.delete();
                                }
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        _log.error("error occured upgrading existing file roots", e);
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
