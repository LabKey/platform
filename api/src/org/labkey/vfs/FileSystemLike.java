package org.labkey.vfs;

import com.fasterxml.jackson.databind.DeserializationContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Path;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.NotFoundException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.labkey.api.util.FileUtil.FILE_SCHEME;

/**
 * In LabKey most files are accessed within a directory with a particular role.  For instance, a directory might be:
 * <br>
 *  - a pipeline root used for storing assay files
 * <br>
 *  - a temporary working directory used for assay import or a report
 * <br>
 *  - a directory with configuration files
 * <p/>
 * In any of these scenarios the code using that directory usually does not need access to files _outside_ that directory.
 * Using java.io.File makes it difficult to enforce this.  Instead of this common pattern
 * <pre>
 *      File workingdir = new File("tempdir");
 *      File file = new File(workingdir, anypath))
 * </pre>
 * We can now follow this pattern, which validates the scope of the resolved path.
 * <pre>
 *     FileLike workingdir = new FileSystemLike.Builder("tempdir").readwrite().root();
 *     FileLike file = workingdir.resolveFile(anypath);
 * </pre>
 *
 * <p/>
 * implementation notes:
 * - This is meant to be a wrapper over java.nio.file.Path, java.io.File or org.apache.commons.vfs2.FileObject or other implementations.
 *   However, it is still lower level than Resource.  For instance, it does not know about Permissions or ContentType, etc.
 * <br>
 * - FileLike objects always present String path and util.Path relative to the FileSystemLike root.
 *   If the FileLike wraps a local path, toNioPath() can be used.
 * <br>
 * - These classes generally do not cache metadata, but the wrapped impl might.  This is why FileLike has a reset() method.
 * - Caching versions can be explicitly requested.
 */
public interface FileSystemLike
{
    // NOTE: a full webdav path consist of case-sensitive and case-insensitive parts
    // However, the relative part of the path into a file system will be consistently sensitive or not
    // These helpers can be used to make the correct Path for this VFS

    org.labkey.api.util.Path parsePath(String str);
    org.labkey.api.util.Path pathOf(org.labkey.api.util.Path path);

    /*
     * Create a file system that return FileLike objects that cache basic file meta-data such as type (file/directory)
     * and direct children.  refresh() can be used to force reload of metadata.
     * TODO See PipelineDirectoryImpl for code that currently does its own caching for performance
     * reasons.
     * FileSystemResource has already been converted to use getCachingFileSystem().
     */
    FileSystemLike getCachingFileSystem();

    default String getScheme()
    {
        return getURI().getScheme();
    }
    URI getURI();
    URI getURI(FileLike fo);
    java.nio.file.Path getNioPath(FileLike fo);

    FileLike getRoot();
    FileLike resolveFile(Path path);        // same as getRoot().resolveFile(path)

    // these methods do not represent permission or specific underlying capability
    // This is requested behavior
    boolean canList();
    boolean canReadFiles();
    boolean canWriteFiles();
    boolean canDeleteRoot();


    default boolean isDescendant(FileLike base, URI uri)
    {
        // handle common case
        if (null == base || getRoot() == base)
            return URIUtil.isDescendant(getURI(), uri);
        if (base.getFileSystem() != this)
            throw new IllegalArgumentException();
        return URIUtil.isDescendant(getURI(base), uri);
    }

    /** BasicFileAttributes uses more memory than we really need, so this is the basics */
    record MinimalFileAttributes(boolean exists, boolean file, boolean directory, long size, long lastModified, long created) {}
    MinimalFileAttributes NULL_ATTRIBUTES = new MinimalFileAttributes(false, false, false, 0, 0, 0);


    class Builder
    {
        URI uri;
        boolean defaultVfs = false;     // for testing
        boolean canList = true;
        boolean canReadFiles = true;
        boolean canWriteFiles = true;
        boolean canDeleteRoot = false;
        boolean memCheck = true;
        boolean caching = false;
        String containerId = null;
        String configName = null;

        public Builder(URI uri)
        {
            this.uri = uri;
        }

        public Builder(File f)
        {
            this.uri = f.toURI();
        }

        public Builder(java.nio.file.Path path)
        {
            this.uri = path.toUri();
        }

        public Builder caching()
        {
            caching = true;
            return this;
        }

        public Builder readonly()
        {
            canReadFiles = true;
            canWriteFiles = false;
            return this;
        }

        public Builder readwrite()
        {
            canReadFiles = true;
            canWriteFiles = true;
            return this;
        }

        public Builder tempDir()
        {
            canDeleteRoot = true;
            return readwrite();
        }

        public Builder vfs()
        {
            defaultVfs = true;
            return this;
        }

        public Builder noMemCheck()
        {
            memCheck = false;
            return this;
        }

        public Builder container(String containerId)
        {
            this.containerId = containerId;
            return this;
        }

        public Builder config(String configName)
        {
            this.configName = configName;
            return this;
        }


        public final String S3_SCHEME = "s3"; //  S3FileSystemProvider.getScheme();

        public FileSystemLike build(DeserializationContext ctx)
        {
            if (null != ctx && S3_SCHEME.equals(uri.getScheme()))
            {
                Map<Path, FileSystemLike> map;
                if (null == (map = (Map<Path, FileSystemLike>) ctx.getAttribute(FileSystemLike.Builder.class.getName())))
                    ctx.setAttribute(FileSystemLike.Builder.class.getName(), (map = new HashMap<>()));
                Path fsKey = new Path(containerId, configName);
                FileSystemLike cloud = map.get(fsKey);
                if (null == cloud)
                {
                    // cloud is always caching we don't care if caching values match
                    cloud = build();
                    map.put(fsKey, cloud);
                }
                return cloud;
            }
            return build();
        }

        public FileSystemLike build()
        {
            var scheme = defaultIfBlank(uri.getScheme(), FILE_SCHEME);

            FileSystemLike ret;
            if (S3_SCHEME.equals(scheme))
            {
                Container c = ContainerManager.getForId(containerId);
                if (null == c)
                    throw new RuntimeException("Container not found: " + containerId);
                ret = CloudStoreService.get().getFileSystemLike(c, configName);
            }
            else if (defaultVfs && !FILE_SCHEME.equals(scheme))
            {
                ret = new FileSystemVFS(uri, canReadFiles, canWriteFiles, canDeleteRoot);
            }
            else
            {
                ret = new FileSystemLocal(uri, canReadFiles, canWriteFiles, canDeleteRoot);
            }

            if (caching)
                ret = ret.getCachingFileSystem();
            if (!memCheck)
            {
                MemTracker.get().remove(ret);
                MemTracker.get().remove(ret.getRoot());
            }
            return ret;
        }
        public FileLike root()
        {
            return build().getRoot();
        }
    }

    /** Helper for partially converted code. Parent dir must exist. */
    static FileLike wrapFile(File f)
    {
        if (f == null)
            return null;
        FileLike p = new Builder(f.getParentFile()).root();
        return p.resolveChild(f.getName());
    }

    static FileLike wrapFile(java.nio.file.Path p)
    {
        if (null == p)
            return null;
        return wrapFile(p.toFile());
    }

    /** Helper for partially converted code. root must exist. */
    static FileLike wrapFile(File root, File f) throws IOException
    {
        if (!root.isDirectory())
            throw new FileNotFoundException(root.getPath());
        FileSystemLike fs = new Builder(root.toURI()).build();
        String rel = FileUtil.relativize(root, f, true);
        return fs.getRoot().resolveFile(Path.parse(rel));
    }

    /** Helper for partially converted code. root must exist. */
    static FileLike wrapFile(java.nio.file.Path root, java.nio.file.Path f) throws IOException
    {
        if (!Files.isDirectory(root))
            throw new FileNotFoundException(root.toString());
        FileSystemLike fs = new Builder(root.toUri()).build();
        URI relative = URIUtil.relativize(root.toUri(), f.toUri());
        if (null == relative)
        {
            throw new IOException("File '" + f.toUri().getPath() + "' is outside the allowed root '" + root.toUri().getPath() + "'");
        }
        return fs.resolveFile(new Path(relative.getPath()));
    }

    /** Helper for partially converted code. May throw if the FileLike does not wrap a local file system. */
    static File toFile(FileLike f)
    {
        if (null == f)
            return null;
        java.nio.file.Path p = f.getFileSystem().getNioPath(f);
        return p.toFile();
    }


    /* More efficient version of wrap when many files may be from the same directory */
    static List<FileLike> wrapFiles(List<File> files)
    {
        Map<File, FileSystemLike> map = new HashMap<>();
        List<FileLike> ret = new ArrayList<>(files.size());
        for (File file : files)
        {
            File parent = file.getParentFile();
            FileSystemLike fs = map.computeIfAbsent(parent, key -> new FileSystemLike.Builder(parent).readwrite().build());
            ret.add(fs.resolveFile(new Path(file.getName())));
        }
        return ret;
    }

    static List<FileLike> wrapPaths(List<java.nio.file.Path> paths)
    {
        Map<File, FileSystemLike> map = new HashMap<>();
        List<FileLike> ret = new ArrayList<>(paths.size());
        for (var path : paths)
        {
            var file = path.toFile();
            File parent = file.getParentFile();
            FileSystemLike fs = map.computeIfAbsent(parent, key -> new FileSystemLike.Builder(parent).readwrite().build());
            ret.add(fs.resolveFile(new Path(file.getName())));
        }
        return ret;
    }

    static Map<String, FileLike> wrapFiles(Map<String, File> files)
    {
        Map<File, FileSystemLike> map = new HashMap<>();
        Map<String, FileLike> ret = files instanceof CaseInsensitiveHashMap<File> ?
                new CaseInsensitiveHashMap<>() :
                new HashMap<>(files.size());
        for (var e : files.entrySet())
        {
            var file = e.getValue();
            File parent = file.getParentFile();
            FileSystemLike fs = map.computeIfAbsent(parent, key -> new FileSystemLike.Builder(parent).readwrite().build());
            ret.put(e.getKey(), fs.resolveFile(new Path(file.getName())));
        }
        return ret;
    }

    /**
     * Deprecated - stop passing around absolute paths through HTTP form submissions, and refactor to use
     * pipeline root relative paths.
     *
     * Verify that the provided path is within the Pipeline for the container and is usable as file
     * @param container scope and context
     * @param filePath to verify
     * @return A FileLike object representation of the provided file path relative to the container's pipeline root
     */
    @Deprecated
    static FileLike getVerifiedFileLike(Container container, String filePath)
    {
        if (filePath == null)
        {
            throw new IllegalArgumentException("File name is required");
        }

        File fileToVerify = new File(filePath);
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(container);
        if (pipeRoot == null)
        {
            throw new NotFoundException("Could not find a pipeline root for '" + container.getPath() + "'");
        }

        FileLike allowedRoot = pipeRoot.getRootFileLike();
        // if root = /a/b/c/ and file = /a/b/c/d/e/f.xlsx, relativeURI = d/e/f.xlsx
        // if root = /a/b/c/ and file = /x/y/z.xlsx, relativeURI = null
        URI relativeURI = URIUtil.relativize(allowedRoot.toURI(), fileToVerify.toURI());

        if (relativeURI == null)
        {
            throw new IllegalArgumentException("File '" + fileToVerify.toURI().getPath() + "' is outside the allowed root '" + allowedRoot.toURI().getPath() + "'");
        }

        if (!allowedRoot.isDescendant(fileToVerify.toURI()))
        {
            throw new IllegalArgumentException("File '" + relativeURI.getPath() + "' is not a descendent of '" + allowedRoot.toURI().getPath() + "'");
        }

        // if root = /a/b/c/ and file = /a/b/c/d/e/f.xlsx - among other things, this essentially checks if '/a/b/c/d/e/f.xlsx' starts with '/a/b/c/'
        return allowedRoot.resolveFile(new Path(relativeURI.getPath()));
    }
}


