package org.labkey.vfs;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Path;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
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
 * - This is meant to be a wrapper over java.nio.file.Path, java.io.File or org.apache.commons.vfs2.FileObject or other implementaions.
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
    record MinimalFileAttributes(boolean exists, boolean file, boolean directory, long size, long lastModified) {}
    MinimalFileAttributes NULL_ATTRIBUTES = new MinimalFileAttributes(false, false, false, 0, 0);


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

        public FileSystemLike build()
        {
            var scheme = defaultIfBlank(uri.getScheme(), FILE_SCHEME);
            FileSystemLike ret;
            if (defaultVfs || !FILE_SCHEME.equals(scheme))
                ret = new FileSystemVFS(uri, canReadFiles, canWriteFiles, canDeleteRoot);
            else
                ret = new FileSystemLocal(uri, canReadFiles, canWriteFiles, canDeleteRoot);
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
        FileLike p = new Builder(f.getParentFile()).root();
        return p.resolveChild(f.getName());
    }

    static FileLike wrapFile(java.nio.file.Path p)
    {
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

    /** Helper for partially converted code. May throw if the FileLike does not wrap a local file system. */
    static File toFile(FileLike f)
    {
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
}


