package org.labkey.assay;

import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.File;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class AssayFilesResourceProvider implements WebdavService.Provider
{
    @Override
    public Set<String> addChildren(WebdavResource target, boolean isListing)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource folder))
            return null;

        FileContentService svc = FileContentService.get();
        if (svc == null)
            return null;

        // Check for the default file location
        Set<String> result = new HashSet<>();
        Container c = folder.getContainer();
        java.nio.file.Path root = svc.getFileRootPath(c);
        if (root != null)
        {
            if (!FileUtil.hasCloudScheme(root) && NetworkDrive.exists(root.toFile()))
            {
                result.add(FileContentService.ASSAY_FILES);
            }
        }

        return result;
    }

    @Override
    public WebdavResource resolve(WebdavResource parent, String name)
    {
        if (!FileContentService.ASSAY_FILES.equalsIgnoreCase(name))
            return null;
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource folder))
            return null;

        Container c = folder.getContainer();
        FileContentService service = FileContentService.get();
        if (null == service)
            return null;
        if (service.isCloudRoot(c))
            return null;

        try
        {
            AttachmentDirectory dir = service.getMappedAttachmentDirectory(c, FileContentService.ContentType.assayfiles, false);
            if (dir != null)
            {
                // don't create the dir here if it doesn't already exist, it will be created the first time an assay run is saved that includes result files
                java.nio.file.Path path = dir.getFileSystemDirectoryPath(c, false);
                if (!Files.exists(path))
                    return null;

                return new AssayFilesResource(parent, Path.toPathPart(name), dir.getFileSystemDirectory(), c);
            }
        }
        catch (MissingRootDirectoryException e)
        {
            // Don't complain here, just hide the @assayfiles subfolder
        }
        catch (RuntimeException e)
        {
            // Don't complain here if AccessDeniedException, just hide the @scripts subfolder (Issue 50212)
            if (!(e.getCause() instanceof AccessDeniedException))
                throw e;
        }

        return null;
    }

    static class AssayFilesResource extends FileSystemResource
    {
        public AssayFilesResource(WebdavResource folder, Path.Part name, File file, SecurableResource resource)
        {
            super(folder, name, file, resource);
        }

        public AssayFilesResource(FileSystemResource folder, Path.Part relativePath)
        {
            super(folder,relativePath);
        }

        @Override
        public WebdavResource find(Path.Part name)
        {
            return new AssayFilesResource(this, name);
        }

        @Override
        protected boolean hasAccess(User user)
        {
            return getPermissions(user).contains(AdminPermission.class);
        }

        @Override
        public boolean canRead(User user, boolean forRead)
        {
            return getPermissions(user).contains(ReadPermission.class);
        }
    }
}
