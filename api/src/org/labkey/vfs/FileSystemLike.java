package org.labkey.vfs;

import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * This is meant to be a wrapper over java.nio.file.Path or java.io.File or org.apache.commons.vfs2.FileObject.
 * However, it is still lower level than Resource.  For instance, it does not know about Permissions or ContentType, etc.
 *
 * <br>
 * FileLike objects always present String path and util.Path relative to the FileSystemLike root.
 * The containing FileSystemLike object just be used to translate to an "external" path.
 * <br>
 * These classes should not cache, but the wrapped impl might.  This is why FileLike has a reset() method.
 */
public interface FileSystemLike
{
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


    class Builder implements org.labkey.api.data.Builder<FileSystemLike>
    {
        URI uri;
        boolean defaultVfs = false;     // for testing
        boolean canList = true;
        boolean canReadFiles = true;
        boolean canWriteFiles = true;
        boolean canDeleteRoot = false;

        public Builder(URI uri)
        {
            this.uri = uri;
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
        public Builder vfs()
        {
            defaultVfs = true;
            return this;
        }
        @Override
        public FileSystemLike build()
        {
            var scheme = defaultIfBlank(uri.getScheme(),"file");
            if (defaultVfs || !"file".equals(scheme))
                return new FileSystemVFS(uri, canReadFiles, canWriteFiles, canDeleteRoot);
            else
                return new FileSystemNIO(uri, canReadFiles, canWriteFiles, canDeleteRoot);
        }
    }

    /** Helper for partially converted code. Parent dir must exist. */
    static FileLike wrapFile(File f) throws IOException
    {
        return wrapFile(f.getParentFile(), f);
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
}


