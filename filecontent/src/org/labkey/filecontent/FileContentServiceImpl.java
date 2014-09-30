/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileListener;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.webdav.WebdavResource;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * User: klum
 * Date: Dec 9, 2009
 */
public class FileContentServiceImpl implements FileContentService, ContainerManager.ContainerListener
{
    static Logger _log = Logger.getLogger(FileContentServiceImpl.class);
    private static final String UPLOAD_LOG = ".upload.log";

    private List<FileListener> _fileListeners = new CopyOnWriteArrayList<>();

    enum Props {
        root,
        rootDisabled,
    }

    enum FileAction
    {
        UPLOAD,
        DELETE
    }

    public FileContentServiceImpl()
    {
    }

    public @Nullable File getFileRoot(Container c, ContentType type)
    {
        switch (type)
        {
            case files:
                String folderName = getFolderName(type);
                File dir = _getFileRoot(c);
                return dir != null ? new File(dir, folderName) : dir;
            
            case pipeline:
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                if (root != null)
                    return root.getRootPath();
                break;
        }
        return null;
    }

    private @Nullable File _getFileRoot(Container c)
    {
        File root = getFileRoot(c);
        if (root != null)
        {
            File dir;

            //Don't want the Project part of the path.
            if (c.isRoot())
                dir = getSiteDefaultRoot();
            else
                dir = root;

            return dir;
        }
        return null;
    }

    public @Nullable File getFileRoot(Container c)
    {
        if (c == null)
            return null;

        if (c.isRoot())
        {
            return getSiteDefaultRoot();
        }

        if (!isFileRootDisabled(c))
        {
            FileRoot root = FileRootManager.get().getFileRoot(c);

            // check if there is a site wide file root
            if (root.getPath() == null || isUseDefaultRoot(c))
            {
                return getDefaultRoot(c, true);
            }
            else
                return new File(root.getPath());
        }
        return null;
    }

    public File getDefaultRoot(Container c, boolean createDir)
    {
        Container firstOverride = getFirstAncestorWithOverride(c);

        File parentRoot;
        if (firstOverride == null)
        {
            parentRoot = getSiteDefaultRoot();
            firstOverride = ContainerManager.getRoot();
        }
        else
        {
            parentRoot = getFileRoot(firstOverride);
        }

        if (parentRoot != null && c != null)
        {
            File fileRoot = new File(parentRoot, getRelativePath(c, firstOverride));

            if (!fileRoot.exists() && createDir)
                fileRoot.mkdirs();

            return fileRoot;
        }
        return null;
    }

    private String getRelativePath(Container c, Container ancestor)
    {
        return c.getPath().replaceAll("^" + Pattern.quote(ancestor.getPath()), "");
    }

    //returns the first parent container that has a custom file root, or NULL if none have overrides
    private Container getFirstAncestorWithOverride(Container c)
    {
        Container toTest = c.getParent();
        if (toTest == null)
            return null;

        while (isUseDefaultRoot(toTest))
        {
            if (toTest == null || toTest.equals(ContainerManager.getRoot()))
                return null;

            toTest = toTest.getParent();
        }

        return toTest;
    }

    public void setFileRoot(Container c, File path)
    {
        if (c.isWorkbookOrTab())
            throw new IllegalArgumentException("File roots cannot be set of workbooks or tabs");
        
        FileRoot root = FileRootManager.get().getFileRoot(c);
        root.setEnabled(true);

        String oldValue = root.getPath();
        String newValue = null;

        // clear out the root
        if (path == null)
            root.setPath(null);
        else
        {
            root.setPath(FileUtil.getAbsoluteCaseSensitiveFile(path).getAbsolutePath());
            newValue = root.getPath();
        }

        FileRootManager.get().saveFileRoot(null, root);
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.WebRoot, oldValue, newValue);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    public void disableFileRoot(Container container)
    {
        if (container == null || container.isRoot())
            throw new IllegalArgumentException("Disabling either a null project or the root project is not allowed.");

        Container effective = getEffectiveContainer(container);
        if (effective != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(effective);
            String oldValue = root.getPath();
            root.setEnabled(false);
            FileRootManager.get().saveFileRoot(null, root);

            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    container, ContainerManager.Property.WebRoot, oldValue, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
    }

    public boolean isFileRootDisabled(Container c)
    {
        if (c == null || c.isRoot())
            _log.error("isFileRootDisabled : The file root of either a null project or the root project cannot be disabled.");

        Container effective = getEffectiveContainer(c);
        if (null == effective)
            return false;

        FileRoot root = FileRootManager.get().getFileRoot(effective);
        return !root.isEnabled();
    }

    public boolean isUseDefaultRoot(Container c)
    {
        if (c == null)
            return true;
        
        Container effective = getEffectiveContainer(c);
        if (null == effective)
            return true;

        FileRoot root = FileRootManager.get().getFileRoot(effective);
        return root.isUseDefault() || StringUtils.isEmpty(root.getPath());
    }

    private Container getEffectiveContainer(Container c)
    {
        return c.isWorkbookOrTab() ? c.getParent() : c;
    }

    public void setIsUseDefaultRoot(Container c, boolean useDefaultRoot)
    {
        Container effective = getEffectiveContainer(c);
        if (effective != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(effective);
            String oldValue = root.getPath();
            root.setEnabled(true);
            root.setUseDefault(useDefaultRoot);
            FileRootManager.get().saveFileRoot(null, root);

            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    effective, ContainerManager.Property.WebRoot, oldValue, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
    }

    public File getSiteDefaultRoot()
    {
        File root = AppProps.getInstance().getFileSystemRoot();

        if (root == null || !root.exists())
            root = getDefaultRoot();

        if (root != null && !root.exists())
            root.mkdirs();

        return root;
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

    public void setSiteDefaultRoot(File root)
    {
        if (root == null || !root.exists())
            throw new IllegalArgumentException("Invalid site root: does not exist");
        
        File prevRoot = getSiteDefaultRoot();
        WriteableAppProps props = AppProps.getWriteableInstance();

        props.setFileSystemRoot(root.getAbsolutePath());
        props.save();

        FileRootManager.get().clearCache();
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                ContainerManager.getRoot(), ContainerManager.Property.SiteRoot, prevRoot, root);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    public FileSystemAttachmentParent registerDirectory(Container c, String name, String path, boolean relative)
    {
        FileSystemAttachmentParent parent = new FileSystemAttachmentParent();
        parent.setContainer(c);
        if (null == name)
            name = path;
        parent.setName(name);
        parent.setPath(path);
        parent.setRelative(relative);
        //We do this because insert does not return new fields
        parent.setEntityid(GUID.makeGUID());

        FileSystemAttachmentParent ret = Table.insert(HttpView.currentContext().getUser(), CoreSchema.getInstance().getMappedDirectories(), parent);
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.AttachmentDirectory, null, ret);
        ContainerManager.firePropertyChangeEvent(evt);
        return ret;
    }

    public void unregisterDirectory(Container c, String name)
    {
        FileSystemAttachmentParent parent = getRegisteredDirectory(c, name);
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Name"), name);
        Table.delete(CoreSchema.getInstance().getMappedDirectories(), filter);
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.AttachmentDirectory, parent, null);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    public AttachmentDirectory getMappedAttachmentDirectory(Container c, boolean createDir) throws UnsetRootDirectoryException, MissingRootDirectoryException
    {
        if (createDir) //force create
            getMappedDirectory(c, true);
        else if (null == getMappedDirectory(c, false))
            return null;

        return new FileSystemAttachmentParent(c, ContentType.files);
    }

    File getMappedDirectory(Container c, boolean create) throws UnsetRootDirectoryException, MissingRootDirectoryException
    {
        File root = getFileRoot(c);
        if (null == root)
        {
            if (create)
                throw new UnsetRootDirectoryException(c.isRoot() ? c : c.getProject());
            else
                return null;
        }

        if (!root.exists())
        {
            if (create)
                throw new MissingRootDirectoryException(c.isRoot() ? c : c.getProject(), root);
            else
                return null;
        }

        File dir = _getFileRoot(c);

        if (!dir.exists() && create)
            dir.mkdirs();

        return dir;
    }

    public FileSystemAttachmentParent getRegisteredDirectory(Container c, String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Name"), name);

        return new TableSelector(CoreSchema.getInstance().getMappedDirectories(), filter, null).getObject(FileSystemAttachmentParent.class);
    }

    public FileSystemAttachmentParent getRegisteredDirectoryFromEntityId(Container c, String entityId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("EntityId"), entityId);

        return new TableSelector(CoreSchema.getInstance().getMappedDirectories(), filter, null).getObject(FileSystemAttachmentParent.class);
    }

    public FileSystemAttachmentParent[] getRegisteredDirectories(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);

        return new TableSelector(CoreSchema.getInstance().getMappedDirectories(), filter, null).getArray(FileSystemAttachmentParent.class);
    }

    public void containerCreated(Container c, User user)
    {
        try
        {
            File dir = getMappedDirectory(c, false);
            //Don't try to create dir if root not configured.
            //But if we should have a directory, create it
            if (null != dir && !dir.exists())
                getMappedDirectory(c, true);
        }
        catch (MissingRootDirectoryException ex)
        {
            /* */
        }
    }

    public void containerDeleted(Container c, User user)
    {
        File dir = null;
        try
        {
            // don't delete the file contents if they have a project override
            if (isUseDefaultRoot(c))
                dir = getMappedDirectory(c, false);
        }
        catch (Exception e)
        {
            _log.error("containerDeleted", e);
        }

        if (null != dir && dir.exists())
        {
            FileUtil.deleteDir(dir);
        }

        ContainerUtil.purgeTable(CoreSchema.getInstance().getMappedDirectories(), c, null);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        // only attempt to move the root if this is a managed file system
        if (isUseDefaultRoot(c))
        {
            File prevParent = _getFileRoot(oldParent);
            if (prevParent != null)
            {
                File src = new File(prevParent, c.getName());
                File dst = _getFileRoot(c);

                if (src.exists() && dst != null)
                    moveFileRoot(src, dst, user, c);
            }
        }
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }

    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
        ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)propertyChangeEvent;
        Container c = evt.container;

        switch (evt.property)
        {
            case Name:          // container rename event
            {
                String oldValue = (String) propertyChangeEvent.getOldValue();
                String newValue = (String) propertyChangeEvent.getNewValue();

                File location = null;
                try
                {
                    location = getMappedDirectory(c, false);
                }
                catch (MissingRootDirectoryException ex)
                {
                    _log.error(ex);
                }
                if (location == null)
                    return;
                //Don't rely on container object. Seems not to point to the
                //new location even AFTER rename. Just construct new file paths
                File parentDir = location.getParentFile();
                File oldLocation = new File(parentDir, oldValue);
                File newLocation = new File(parentDir, newValue);
                if (newLocation.exists())
                    moveToDeleted(newLocation);

                if (oldLocation.exists())
                {
                    oldLocation.renameTo(newLocation);
                    fireFileMoveEvent(oldLocation, newLocation, evt.user, evt.container);
                }
                break;
            }
        }
    }

    public @Nullable String getFolderName(FileContentService.ContentType type)
    {
        if (type != null)
            return "@" + type.name();
        return null;
    }


    /**
     * Move the file or directory into a ".deleted" directory under the parent directory.
     * @return True if succesfully moved.
     */
    private static boolean moveToDeleted(File fileToMove)
    {
        if (!fileToMove.exists())
            return false;

        File parent = fileToMove.getParentFile();

        File deletedDir = new File(parent, ".deleted");
        if (!deletedDir.exists())
            if (!deletedDir.mkdir())
                return false;

        File newLocation = new File(deletedDir, fileToMove.getName());
        if (newLocation.exists())
            FileUtil.deleteDir(newLocation);

        return fileToMove.renameTo(newLocation);
    }

    static void logFileAction(File directory, String fileName, FileAction action, User user)
    {
        try (FileWriter fw = new FileWriter(new File(directory, UPLOAD_LOG), true))
        {
            fw.write(action.toString() + "\t" + fileName + "\t" + new Date() + "\t" + (user == null ? "(unknown)" : user.getEmail()) + "\n");
        }
        catch (Exception x)
        {
            //Just log it.
            _log.error(x);
        }
    }

    public FilesAdminOptions getAdminOptions(Container c)
    {
        FileRoot root = FileRootManager.get().getFileRoot(c);
        String xml = null;

        if (!StringUtils.isBlank(root.getProperties()))
        {
            xml = root.getProperties();
        }
        return new FilesAdminOptions(c, xml);
    }

    public void setAdminOptions(Container c, FilesAdminOptions options)
    {
        if (options != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(c);

            root.setProperties(options.serialize());
            FileRootManager.get().saveFileRoot(null, root);
        }
    }

    public static final String NAMESPACE_PREFIX = "FileProperties";
    public static final String PROPERTIES_DOMAIN = "File Properties";
    public static final String TYPE_PROPERTIES = "FileProperties";

    public String getDomainURI(Container container)
    {
        return getDomainURI(container, getAdminOptions(container).getFileConfig());
    }

    public String getDomainURI(Container container, FilesAdminOptions.fileConfig config)
    {
        while (config == FilesAdminOptions.fileConfig.useParent && container != container.getParent())
        {
            container = container.getParent();
            config = getAdminOptions(container).getFileConfig();
        }

        //String typeURI = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":List" + ".Folder-" + container.getRowId() + ":" + name;

        return new Lsid("urn:lsid:labkey.com:" + NAMESPACE_PREFIX + ".Folder-" + container.getRowId() + ':' + TYPE_PROPERTIES).toString();
    }

    public ExpData getDataObject(WebdavResource resource, Container c)
    {
        return getDataObject(resource, c, null, false);
    }

    public static ExpData getDataObject(WebdavResource resource, Container c, User user, boolean create)
    {
        if (resource != null)
        {
            File file = resource.getFile();
            ExpData data = ExperimentService.get().getExpDataByURL(file, c);

            if (data == null && create)
            {
                data = ExperimentService.get().createData(c, new DataType("UploadedFile"));
                data.setName(file.getName());
                data.setDataFileURI(file.toURI());
                data.save(user);
            }
            return data;
        }
        return null;
    }

    @Override
    public QueryUpdateService getFilePropsUpdateService(TableInfo tinfo, Container container)
    {
        return new FileQueryUpdateService(tinfo, container);
    }

    public boolean isValidProjectRoot(String root)
    {
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
        {
            return false;
        }
        return true;
    }

    public void moveFileRoot(File prev, File dest, @Nullable User user, @Nullable Container container)
    {
        try
        {
            _log.info("moving " + prev.getPath() + " to " + dest.getPath());
            boolean doRename = true;

            // attempt to rename, if that fails (try the more expensive copy)
            if (dest.exists())
                doRename = dest.delete();

            if (doRename && !prev.renameTo(dest))
            {
                _log.info("rename failed, attempting to copy");

                //listFiles can return null, which could cause a NPE
                if(prev.listFiles() != null)
                {
                    for (File file : prev.listFiles())
                        FileUtil.copyBranch(file, dest);
                }
                FileUtil.deleteDir(prev);
            }
            fireFileMoveEvent(prev, dest, user, container);
        }
        catch (IOException e)
        {
            _log.error("error occurred moving the file root", e);
        }
    }

    @Override
    public void fireFileCreateEvent(@NotNull File created, @Nullable User user, @Nullable Container container)
    {
        created = FileUtil.getAbsoluteCaseSensitiveFile(created);
        for (FileListener fileListener : _fileListeners)
        {
            fileListener.fileCreated(created, user, container);
        }
    }

    public void fireFileMoveEvent(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
    {
        // Make sure that we've got the best representation of the file that we can
        src = FileUtil.getAbsoluteCaseSensitiveFile(src);
        dest = FileUtil.getAbsoluteCaseSensitiveFile(dest);
        for (FileListener fileListener : _fileListeners)
        {
            fileListener.fileMoved(src, dest, user, container);
        }
    }

    @Override
    public void addFileListener(FileListener listener)
    {
        _fileListeners.add(listener);
    }

    @Override
    public Map<String, Collection<File>> listFiles(@NotNull Container container)
    {
        Map<String, Collection<File>> files = new LinkedHashMap<>();
        for (FileListener fileListener : _fileListeners)
        {
            files.put(fileListener.getSourceName(), new HashSet<>(fileListener.listFiles(container)));
        }
        return files;
    }

    @Override
    public SQLFragment listFilesQuery(@NotNull User currentUser)
    {
        SQLFragment frag = new SQLFragment();
        if (currentUser == null || !currentUser.isSiteAdmin())
        {
            frag.append("SELECT\n");
            frag.append("  CAST(NULL AS VARCHAR) AS Container,\n");
            frag.append("  NULL AS Created,\n");
            frag.append("  NULL AS CreatedBy,\n");
            frag.append("  NULL AS Modified,\n");
            frag.append("  NULL AS ModifiedBy,\n");
            frag.append("  NULL AS FilePath,\n");
            frag.append("  NULL AS SourceKey,\n");
            frag.append("  NULL AS SourceName\n");
            frag.append("WHERE 1 = 0");
        }
        else
        {
            String union = "";
            frag.append("(");
            for (FileListener fileListener : _fileListeners)
            {
                SQLFragment subselect = fileListener.listFilesQuery();
                if (subselect != null)
                {
                    frag.append(union);
                    frag.append(subselect);
                    union = "UNION\n";
                }
            }
            frag.append(")");
        }
        return frag;
    }

    public static FileContentServiceImpl getInstance()
    {
        return (FileContentServiceImpl) ServiceRegistry.get(FileContentService.class);
    }

    public static class TestCase extends AssertionError
    {
        private static final String TRICKY_CHARACTERS_FOR_PROJECT_NAMES = "\u2603~!@$&()_+{}-=[],.#\u00E4\u00F6\u00FC";

        private static final String PROJECT1 = "FileRootTestProject1" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
        private static final String PROJECT1_SUBFOLDER1 = "Subfolder1";
        private static final String PROJECT1_SUBFOLDER2 = "Subfolder2" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
        private static final String PROJECT1_SUBSUBFOLDER = "SubSubfolder";
        private static final String PROJECT2 = "FileRootTestProject2";

        private static final String FILE_ROOT_SUFFIX = "_FileRootTest";
        private static final String TXT_FILE = "FileContentTestFile.txt";

        private Map<Container, File> _expectedPaths;

        @Test
        public void fileRootsTest() throws Exception
        {
            //pre-clean
            cleanup();

            _expectedPaths = new HashMap<>();

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT1);
            _expectedPaths.put(project1, null);

            Container project2 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT2);
            _expectedPaths.put(project2, null);

            Container subfolder1 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER1);
            _expectedPaths.put(subfolder1, null);

            Container subfolder2 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER2);
            _expectedPaths.put(subfolder2, null);

            Container subsubfolder = ContainerManager.createContainer(subfolder1, PROJECT1_SUBSUBFOLDER);
            _expectedPaths.put(subsubfolder, null);

            //set custom root on project, then expect children to inherit
            File testRoot = getTestRoot();

            svc.setFileRoot(project1, testRoot);
            _expectedPaths.put(project1, testRoot);

            //the subfolder should inherit from the parent
            _expectedPaths.put(subfolder1, new File(testRoot, subfolder1.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(subfolder1), svc.getDefaultRoot(subfolder1, false));
            assertPathsEqual("Subfolder1 has incorrect root", _expectedPaths.get(subfolder1), svc.getFileRoot(subfolder1));

            _expectedPaths.put(subfolder2, new File(testRoot, subfolder2.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(subfolder2), svc.getDefaultRoot(subfolder2, false));
            assertPathsEqual("Subfolder2 has incorrect root", _expectedPaths.get(subfolder2), svc.getFileRoot(subfolder2));

            _expectedPaths.put(subsubfolder, new File(_expectedPaths.get(subfolder1), subsubfolder.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(subsubfolder), svc.getDefaultRoot(subsubfolder, false));
            assertPathsEqual("SubSubfolder has incorrect root", _expectedPaths.get(subsubfolder), svc.getFileRoot(subsubfolder));

            //override root on 1st child, expect children of that folder to inherit
            _expectedPaths.put(subfolder1, new File(testRoot, "CustomSubfolder"));
            _expectedPaths.get(subfolder1).mkdirs();
            svc.setFileRoot(subfolder1, _expectedPaths.get(subfolder1));
            assertPathsEqual("SubSubfolder has incorrect root", new File(_expectedPaths.get(subfolder1), subsubfolder.getName()), svc.getFileRoot(subsubfolder));

            //reset project, we assume overridden child roots to remain the same
            svc.setFileRoot(project1, null);
            assertPathsEqual("Subfolder1 has incorrect root", _expectedPaths.get(subfolder1), svc.getFileRoot(subfolder1));
            assertPathsEqual("SubSubfolder has incorrect root", new File(_expectedPaths.get(subfolder1), subsubfolder.getName()), svc.getFileRoot(subsubfolder));

        }

        private void assertPathsEqual(String msg, File expected, File actual)
        {
            String expectedPath = FileUtil.getAbsoluteCaseSensitiveFile(expected).getPath();
            String actualPath = FileUtil.getAbsoluteCaseSensitiveFile(actual).getPath();
            Assert.assertEquals(msg, expectedPath, actualPath);
        }

        private File getTestRoot()
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            File siteRoot = svc.getSiteDefaultRoot();
            File testRoot = new File(siteRoot, FILE_ROOT_SUFFIX);
            testRoot.mkdirs();
            Assert.assertTrue("Unable to create test file root", testRoot.exists());

            return testRoot;
        }

        @Test
        //when we move a folder, we expect child files to follow, and expect
        // any file paths stored in the DB to also get updated
        public void testFolderMove() throws Exception
        {
            //pre-clean
            cleanup();

            _expectedPaths = new HashMap<>();

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT1);
            _expectedPaths.put(project1, null);

            Container project2 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT2);
            _expectedPaths.put(project2, null);

            Container subfolder1 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER1);
            _expectedPaths.put(subfolder1, null);

            Container subfolder2 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER2);
            _expectedPaths.put(subfolder2, null);

            Container subsubfolder = ContainerManager.createContainer(subfolder1, PROJECT1_SUBSUBFOLDER);
            _expectedPaths.put(subsubfolder, null);

            //create a test file that we will follow
            File fileRoot = svc.getFileRoot(subsubfolder, ContentType.files);
            fileRoot.mkdirs();

            File childFile = new File(fileRoot, TXT_FILE);
            childFile.createNewFile();

            ExpData data = ExperimentService.get().createData(subsubfolder, new DataType("FileContentTest"));
            data.setDataFileURI(childFile.toURI());
            data.save(TestContext.get().getUser());
            int rowId = data.getRowId();

            Assert.assertTrue("File not found: " + childFile.getPath(), childFile.exists());
            ContainerManager.move(subsubfolder, subfolder2, TestContext.get().getUser());
            Container movedSubfolder = ContainerManager.getChild(subfolder2, subsubfolder.getName());

            _expectedPaths.put(movedSubfolder, new File(svc.getFileRoot(subfolder2), movedSubfolder.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(movedSubfolder), svc.getDefaultRoot(movedSubfolder, false));
            assertPathsEqual("SubSubfolder has incorrect root", _expectedPaths.get(movedSubfolder), svc.getFileRoot(movedSubfolder));

            File expectedFile = new File(svc.getFileRoot(movedSubfolder, ContentType.files), TXT_FILE);
            Assert.assertTrue("File was not moved, expected: " + expectedFile.getPath(), expectedFile.exists());

            ExpData movedData = ExperimentService.get().getExpData(rowId);
            Assert.assertNotNull(movedData);

            assertPathsEqual("Incorrect file path", expectedFile, movedData.getFile());
        }

        @Test
        public void testWorkbooksAndTabs() throws Exception
        {
            //pre-clean
            cleanup();

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT1);

            Container workbook = ContainerManager.createContainer(project1, null, null, null, Container.TYPE.workbook, TestContext.get().getUser());
            File expectedWorkbookRoot = new File(svc.getFileRoot(project1), workbook.getName());
            assertPathsEqual("Workbook has incorrect file root", expectedWorkbookRoot, svc.getFileRoot(workbook));

            Container tab = ContainerManager.createContainer(project1, "tab", null, null, Container.TYPE.tab, TestContext.get().getUser());
            File expectedTabRoot = new File(svc.getFileRoot(project1), tab.getName());
            assertPathsEqual("Folder tab has incorrect file root", expectedTabRoot, svc.getFileRoot(tab));
        }


            @After
        public void cleanup()
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.getForPath(PROJECT1);
            if (project1 != null)
            {
                ContainerManager.deleteAll(project1, TestContext.get().getUser());

                File file1 = svc.getFileRoot(project1);
                if (file1 != null && file1.exists())
                {
                    FileUtil.deleteDir(file1);
                }
            }

            Container project2 = ContainerManager.getForPath(PROJECT2);
            if (project2 != null)
            {
                ContainerManager.deleteAll(project2, TestContext.get().getUser());

                File file2 = svc.getFileRoot(project2);
                if (file2 != null && file2.exists())
                {
                    FileUtil.deleteDir(file2);
                }
            }

            File testRoot = getTestRoot();
            if (testRoot.exists())
            {
                FileUtil.deleteDir(testRoot);
            }
        }
    }
}
