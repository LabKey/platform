package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
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
import java.util.List;
import java.util.Set;

public class ScriptsResourceProvider implements WebdavService.Provider
{
    @Override
    public @Nullable Set<String> addChildren(@NotNull WebdavResource target, boolean isListing)
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
                result.add(FileContentService.SCRIPTS_LINK);
            }
        }

        return result;
    }

    @Override
    public WebdavResource resolve(@NotNull WebdavResource parent, @NotNull String name)
    {
        if (!FileContentService.SCRIPTS_LINK.equalsIgnoreCase(name))
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
            AttachmentDirectory dir = service.getMappedAttachmentDirectory(c, FileContentService.ContentType.scripts, false);
            if (dir != null)
            {
                return new ScriptsResource(parent, Path.toPathPart(name), dir.getFileSystemDirectory(), c.getPolicy());
            }
        }
        catch (MissingRootDirectoryException e)
        {
            // Don't complain here, just hide the @scripts subfolder
        }
        catch (RuntimeException e)
        {
            // Don't complain here if AccessDeniedException, just hide the @scripts subfolder (Issue 50212)
            if (!(e.getCause() instanceof AccessDeniedException))
                throw e;
        }

        return null;
    }

    static class ScriptsResource extends FileSystemResource
    {
        public ScriptsResource(WebdavResource folder, Path.Part name, File file, SecurityPolicy policy)
        {
            super(folder, name, file, policy);
        }

        public ScriptsResource(FileSystemResource folder, Path.Part relativePath)
        {
            super(folder,relativePath);
        }

        @Override
        public WebdavResource find(Path.Part name)
        {
            return new ScriptsResource(this, name);
        }

        @Override
        protected boolean hasAccess(User user)
        {
            return user.isPlatformDeveloper();
        }

        @Override
        public boolean canRead(User user, boolean forRead)
        {
            return hasAccess(user);
        }

        @Override
        public boolean canWrite(User user, boolean forWrite)
        {
            return hasAccess(user);
        }

        @Override
        public boolean canCreate(User user, boolean forCreate)
        {
            return hasAccess(user);
        }

        @Override
        public boolean canDelete(User user, boolean forDelete, @Nullable List<String> message)
        {
            return hasAccess(user);
        }

        @Override
        public boolean canRename(User user, boolean forRename)
        {
            return hasAccess(user);
        }

        @Override
        public boolean canList(User user, boolean forRead)
        {
            return hasAccess(user);
        }

        @Override
        public boolean shouldIndex()
        {
            return false;
        }
    }
}
