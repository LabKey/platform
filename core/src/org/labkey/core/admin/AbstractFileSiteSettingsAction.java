/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
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
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.files.FileContentService.UPLOADED_FILE;

/**
 * User: jeckels
 * Date: Sep 26, 2011
 */
public abstract class AbstractFileSiteSettingsAction<FormType extends FileSettingsForm> extends FormViewAction<FormType>
{
    private static Logger _log = LogManager.getLogger(FilesSiteSettingsAction.class);
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

            try {
                boolean isNewRoot = isNewRoot(_svc.getSiteDefaultRoot(), f);
                if (!NetworkDrive.exists(f) || !f.isDirectory())
                {
                    errors.reject(SpringActionController.ERROR_MSG, "File root '" + webRoot + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                }
                else if (isNewRoot && !form.isUpgrade())
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

        if (form.isUpgrade())
        {
            upgradeExistingFileSets();
        }
        else if (isNewRoot(prev, _svc.getSiteDefaultRoot()))
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
        SiteSettingsAuditProvider.SiteSettingsAuditEvent event = new SiteSettingsAuditProvider.SiteSettingsAuditEvent(ContainerManager.getRoot().getId(), "The setting for disable file upload was changed (see details).");

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

    private void upgradeExistingFileSets()
    {
        _log.info("Upgrading existing file roots to @files");

        Map<String, Container> fileSets = new HashMap<>();
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
                                        FileUtil.mkdirs(dest);

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
                        _log.error("error occurred upgrading existing file roots", e);
                    }
                }
            }

            // after the files have been moved, transfer metadata from the old attachment documents into the
            // exp data table and delete the old attachment records
            Container c = entry.getValue();
            try
            {
                if (!_svc.isUseDefaultRoot(c))
                {
                    // prior to 10.1 there were no default roots, everything was an override
                    AttachmentDirectory root = _svc.getMappedAttachmentDirectory(c, false);

                    if (root != null)
                    {
                        for (Attachment a : AttachmentService.get().getAttachments(root))
                        {
                            File file = a.getFile();
                            if (file != null && file.exists() && a.getCreatedBy() != 0)
                            {
                                User user = UserManager.getUser(a.getCreatedBy());
                                ExpData data = ExperimentService.get().getExpDataByURL(file, c);

                                if ((user != null && !user.isGuest()) && data == null)
                                {
                                    data = ExperimentService.get().createData(c, UPLOADED_FILE);
                                    data.setDataFileURI(file.toURI());
                                    data.setName(file.getName());
                                    data.save(user);
                                }
                            }
                        }
                        // remove the old attachments
                        TableInfo tInfo = CoreSchema.getInstance().getTableInfoDocuments();
                        new SqlExecutor(tInfo.getSchema()).execute("DELETE FROM " + tInfo + " WHERE Parent = ?", root.getEntityId());
                    }
                }
            }
            catch (Exception e)
            {
                _log.error("error occurred migrating file content metadata to WebDav", e);
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
            _log.error("error occurred upgrading existing file sets", e);
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

}
