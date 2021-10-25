/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.file.SimplePathVisitor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.Crypt;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: jeckels
 * Date: Dec 5, 2005
 */
public class FileUtil
{
    private static final Logger LOG = LogManager.getLogger(FileUtil.class);

    private static File _tempDir = null;

    @Deprecated
    public static boolean deleteDirectoryContents(File dir)
    {
        try
        {
            return deleteDirectoryContents(dir.toPath());
        }
        catch (IOException e)
        {
            return false; // could there be more done here to log the error?
        }
    }

    public static boolean deleteDirectoryContents(Path dir) throws IOException
    {
        return deleteDirectoryContents(dir, null);
    }

    public static boolean deleteDirectoryContents(Path dir, @Nullable Logger log) throws IOException
    {
        if (Files.isDirectory(dir))
        {
            File dirFile = dir.toFile(); //TODO this method should be converted to use Path and Files.walkFileTree
            String[] children = dirFile.list();

            if (null == children) // 17562
                return true;

            for (String aChildren : children)
            {
                boolean success = deleteDir(new File(dirFile, aChildren), log);
                if (!success)
                {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean deleteSubDirs(File dir)
    {
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (String aChildren : children)
            {
                boolean success = true;
                File child = new File(dir, aChildren);
                if (child.isDirectory())
                    success = deleteDir(child);
                if (!success)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** File.delete() will only delete a directory if it's empty, but this will
     * delete all the contents and the directory */
    public static boolean deleteDir(File dir)
    {
        return deleteDir(dir, null);
    }

    @Deprecated
    public static boolean deleteDir(@NotNull File dir, Logger log)
    {
        return deleteDir(dir.toPath(), log);
    }

    public static boolean deleteDir(Path dir, Logger log)
    {
        //TODO seems like this could be reworked to use Files.walkFileTree
        log = log == null ? LOG : log;

        // Issue 22336: See note in FileUtils.isSymLink() about windows-specific bugs for symlinks:
        // http://commons.apache.org/proper/commons-io/apidocs/org/apache/commons/io/FileUtils.html
        if (!Files.isSymbolicLink(dir))
        {
            try
            {
                // this returns true if !dir.isDirectory()
                boolean success = deleteDirectoryContents(dir, log);
                if (!success)
                    return false;
            }
            catch (IOException e)
            {
                log.debug(String.format("Unable to clean dir [%1$s]", dir.toString()), e);
                return false;
            }
        }

        IOException lastException = null;

        // The directory is now either a sym-link or empty, so delete it
        for (int i = 0; i < 5 ; i++)
        {
            try
            {
                Files.deleteIfExists(dir);
                return true;
            }
            catch (IOException e)
            {
                lastException = e;
                // Issue 39579: Folder import sometimes fails to delete temp directory
                // wait a little then try again
                log.warn("Failed to delete file. Sleep and try to delete again. " + e.getMessage());
                try {Thread.sleep(1000);} catch (InterruptedException x) {/* pass */}
            }
        }
        log.warn("Failed to delete file after 5 attempts: " + FileUtil.getAbsoluteCaseSensitiveFile(dir.toFile()), lastException);
        return false;
    }

    public static void deleteDir(@NotNull Path dir) throws IOException
    {
        if (Files.exists(dir))
        {
            if (hasCloudScheme(dir))
            {
                // TODO: On Windows, collect is yielding AccessDenied Exception, so only do this for cloud
                for (Path path : Files.walk(dir).sorted(Comparator.reverseOrder()).collect(Collectors.toList()))
                {
                    Files.deleteIfExists(path);
                }
            }
            else
            {
                deleteDir(dir.toFile());    // Note: we maintain existing behavior from before Path work, which is to ignore any error
            }
        }
    }

    public static void copyDirectory(Path srcPath, Path destPath) throws IOException
    {
        // Will replace existing files
        if (!Files.exists(destPath))
            Files.createDirectory(destPath);
        try (Stream<Path> list = Files.list(srcPath))
        {
            for (Path srcChild : list.collect(Collectors.toList()))
            {
                Path destChild = destPath.resolve(getFileName(srcChild));
                if (Files.isDirectory(srcChild))
                    copyDirectory(srcChild, destChild);
                else
                    Files.copy(srcChild, destChild, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // return true if file exists and is not a directory
    public static boolean isFileAndExists(@Nullable Path path)
    {
        try
        {
            // One call to cloud rather than two (exists && !isDirectory)
            return (null != path && !Files.readAttributes(path, BasicFileAttributes.class).isDirectory());
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * Remove text right of a specific number of periods, including the periods, from a file's name.
     * <ul>
     *  <li>C:\dir\name.ext, 1 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 2 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 1 => name.ext1</li>
     * </ul>
     *
     * @param fileName name of the file
     * @param dots number of dots to remove
     * @return base name
     */
    public static String getBaseName(String fileName, int dots)
    {
        String baseName = fileName;
        while (dots-- > 0 && baseName.indexOf('.') != -1)
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        return baseName;
    }

    /**
     * Remove text right of and including the last period in a file's name.
     * @param fileName name of the file
     * @return base name
     */
    public static String getBaseName(String fileName)
    {
        return getBaseName(fileName, 1);
    }

    /**
     * Remove text right of a specific number of periods, including the periods, from a file's name.
     * <ul>
     *  <li>C:\dir\name.ext, 1 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 2 => name</li>
     *  <li>C:\dir\name.ext1.ext2, 1 => name.ext1</li>
     * </ul>
     *
     * @param file file from which to get the name
     * @param dots number of dots to remove
     * @return base name
     */
    public static String getBaseName(File file, int dots)
    {
        return getBaseName(file.getName(), dots);
    }

    /**
     * Remove text right of and including the last period in a file's name.
     * @param file file from which to get the name
     * @return base name
     */
    public static String getBaseName(File file)
    {
        return getBaseName(file, 1);
    }

    /**
     * Returns the file name extension without the dot, null if there
     * isn't one.
     */
    public static String getExtension(File file)
    {
        return getExtension(file.getName());
    }

    /**
     * Returns the file name extension without the dot, null if there
     * isn't one.
     */
    public static String getExtension(String name)
    {
        if (name != null && name.lastIndexOf('.') != -1)
        {
            return name.substring(name.lastIndexOf('.') + 1, name.length());
        }
        return null;
    }

    public static boolean hasCloudScheme(Path path)
    {
        try
        {
            return hasCloudScheme(path.toUri());
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static boolean hasCloudScheme(URI uri)
    {
        return "s3".equalsIgnoreCase(uri.getScheme());
    }

    public static boolean hasCloudScheme(String url)
    {
        return url.toLowerCase().startsWith("s3://");
    }

    public static String getAbsolutePath(Path path)
    {
        if (!FileUtil.hasCloudScheme(path))
            return path.toFile().getAbsolutePath();
        else
            return getPathStringWithoutAccessId(path.toAbsolutePath().toUri());

    }

    @Nullable
    public static String getAbsolutePath(Container container, Path path)
    {   // Returned string is NOT necessarily a URI (i.e. it is not encoded)
        return getAbsolutePath(container, path.toUri());
    }

    @Nullable
    public static String getAbsolutePath(Container container, URI uri)
    {
        if (!uri.isAbsolute())
            return null;
        else if (!FileUtil.hasCloudScheme(uri))
            return new File(uri).getAbsolutePath();
        else
            return getAbsolutePathWithoutAccessIdFromCloudUrl(container, uri);
    }

    @Nullable
    public static String getAbsoluteCaseSensitivePathString(Container container, URI uri)
    {
        if (!uri.isAbsolute())
            return null;
        else if (!FileUtil.hasCloudScheme(uri))
            return getAbsoluteCaseSensitiveFile(new File(uri)).toPath().toUri().toString();    // Was:  return getAbsoluteCaseSensitiveFile(new File(uri)).toURI().toString(); // #36352
        else
            return getAbsolutePathWithoutAccessIdFromCloudUrl(container, uri);
    }

    @Nullable
    public static Path getAbsoluteCaseSensitivePath(Container container, URI uri)
    {
        if (!uri.isAbsolute())
            return null;
        else if (!FileUtil.hasCloudScheme(uri))
            return getAbsoluteCaseSensitiveFile(new File(uri)).toPath();
        else
            return getAbsolutePathFromCloudUrl(container, uri);
    }

    @Nullable
    private static String getAbsolutePathWithoutAccessIdFromCloudUrl(Container container, URI uri)
    {
        Path path = getAbsolutePathFromCloudUrl(container, uri);
        return null != path ? getPathStringWithoutAccessId(path.toAbsolutePath().toUri()) : null;
    }

    @Nullable
    private static Path getAbsolutePathFromCloudUrl(Container container, URI uri)
    {
        Path path = CloudStoreService.get().getPathFromUrl(container, uri.toString());
        return null != path ? path.toAbsolutePath() : null;
    }

    public static Path getAbsoluteCaseSensitivePath(Container container, Path path)
    {
        if (!FileUtil.hasCloudScheme(path))
            return getAbsoluteCaseSensitiveFile(path.toFile()).toPath();
        else
            return path.toAbsolutePath();
    }

    @Nullable
    public static Path getPath(Container container, URI uri)
    {
        if (!uri.isAbsolute())
            return null;
        else if (!FileUtil.hasCloudScheme(uri))
            return new File(uri).toPath();
        else
            return CloudStoreService.get().getPathFromUrl(container, uri.toString());
    }

    public static URI createUri(String str)
    {
        return createUri(str, true);
    }

    public static URI createUri(String str, boolean isEncoded)
    {
        str = str.replace("\\", "/");
        if (str.matches("^[A-z]:/.*"))
            return new File(str).toURI();

        String str2 = str;
        if (str2.startsWith("/"))
            str2 = "file://" + str;

        // Creating stack traces is expensive so only bother if we're really going to log it
        if (LOG.isDebugEnabled())
        {
            LOG.debug("CreateUri from: " + str + " [" + Thread.currentThread().getStackTrace()[2].toString() + "]");
        }
        if (isEncoded)
            str2 = str2.replace(" ", "%20"); // Spaces in paths make URI unhappy
        else
            str2 = encodeForURL(str2);
        try
        {
            return new URI(str2);
        }
        catch (URISyntaxException e)
        {
            // We're handling encoded and unencoded, so this can fail because of certain reserved chars;
            if (str.startsWith("/"))
                return new File(str).toPath().toUri();
            throw new IllegalArgumentException(e);
        }
    }

    public static String getFileName(Path fullPath)
    {
        // We want unencoded fileName
        if (hasCloudScheme(fullPath))
        {
            return fullPath.getFileName().toUri().getPath();
        }
        else
        {
            return fullPath.getFileName().toString();
        }
    }

    public static String decodeSpaces(@NotNull String str)
    {
        return str.replace("%20", " ");
    }

    public static String pathToString(Path path)
    {   // Returns a URI string (encoded)
        return getPathStringWithoutAccessId(path.toUri());
    }

    public static String uriToString(URI uri)
    {
        return getPathStringWithoutAccessId(uri);
    }

    public static Path stringToPath(Container container, String str)
    {
        return stringToPath(container, str, true);
    }

    public static Path stringToPath(Container container, String str, boolean isEncoded)
    {
        if (!FileUtil.hasCloudScheme(str))
            return new File(createUri(str, isEncoded)).toPath();
        else
            return CloudStoreService.get().getPathFromUrl(container, PageFlowUtil.decode(str)/*decode everything not just the space*/);
    }

    public static String getCloudRootPathString(String cloudName)
    {
        return FileContentService.CLOUD_ROOT_PREFIX + "/" + cloudName;
    }

    @Nullable
    private static String getPathStringWithoutAccessId(URI uri)
    {
        if (null != uri)
            if (hasCloudScheme(uri))
                return uri.toString().replaceFirst("/\\w+@s3", "/s3");      // Remove accessId portion if exists
            else
            {
                try
                {
                    return URIUtil.normalizeUri(uri).toString();
                }
                catch (URISyntaxException e)
                {
                    LOG.debug("Error attempting to conform uri: " + e.getMessage());
                    return uri.toString();
                }
            }
        else
            return null;
    }

    /**
     * Get relative path of File 'file' with respect to 'home' directory
     * <p><pre>
     * example : home = /a/b/c
     *           file    = /a/d/e/x.txt
     *           return = ../../d/e/x.txt
     * </pre><p>
     * The path returned has system specific directory separators.
     * <p>
     * It is equivalent to:<br>
     * <pre>home.toURI().relativize(f.toURI).toString().replace('/', File.separatorChar)</pre>
     *
     * @param home base path, should be a directory, not a file, or it doesn't make sense
     * @param file    file to generate path for
     * @param canonicalize whether or not the paths need to be canonicalized
     * @return path from home to file as a string
     */
    public static String relativize(File home, File file, boolean canonicalize) throws IOException
    {
        if (canonicalize)
        {
            home = home.getCanonicalFile();
            file = file.getCanonicalFile();
        }
        else
        {
            home = resolveFile(home);
            file = resolveFile(file);
        }
        return matchPathLists(getPathList(home), getPathList(file));
    }

    /**
     * Get a relative path of File 'file' with respect to 'home' directory,
     * forcing Unix (i.e. URI) forward slashes for directory separators.
     * <p>
     * This is a lot like <cod>URIUtil.relativize()</code> without requiring
     * that the file be a descendant of the base.
     * <p>
     * It is equivalent to:<br>
     * <pre>home.toURI().relativize(f.toURI).toString()</pre>
     */
    public static String relativizeUnix(File home, File f, boolean canonicalize) throws IOException
    {
        return relativize(home, f, canonicalize).replace('\\', '/');
    }

    public static String relativizeUnix(Path home, Path f, boolean canonicalize) throws IOException
    {
        if (!hasCloudScheme(home) && !hasCloudScheme(f))
            return relativizeUnix(home.toFile(), f.toFile(), canonicalize);
        return getPathStringWithoutAccessId(home.toUri().relativize(f.toUri()));
    }

    /**
     * Break a path down into individual elements and add to a list.
     * <p/>
     * example : if a path is /a/b/c/d.txt, the breakdown will be [d.txt,c,b,a]
     *
     * @param file input file
     * @return a List collection with the individual elements of the path in reverse order
     */
    private static List<String> getPathList(File file)
    {
        List<String> parts = new ArrayList<>();
        while (file != null)
        {
            parts.add(file.getName());
            file = file.getParentFile();
        }

        return parts;
    }


    /**
     * Figure out a string representing the relative path of
     * 'file' with respect to 'home'
     *
     * @param home home path
     * @param file path of file
     * @return relative path from home to file
     */
    public static String matchPathLists(List<String> home, List<String> file)
    {
        // start at the beginning of the lists
        // iterate while both lists are equal
        StringBuilder path = new StringBuilder();
        int i = home.size() - 1;
        int j = file.size() - 1;

        // first eliminate common root
        while ((i >= 0) && (j >= 0) && (home.get(i).equals(file.get(j))))
        {
            i--;
            j--;
        }

        // for each remaining level in the home path, add a ..
        for (; i >= 0; i--)
            path.append("..").append(File.separator);

        // for each level in the file path, add the path
        for (; j >= 1; j--)
            path.append(file.get(j)).append(File.separator);

        // if nothing left of the file, then it was a directory
        // of which home is a subdirectory.
        if (j < 0)
        {
            if (path.length() == 0)
                path.append(".");
            else
                path.delete(path.length() - 1, path.length());  // remove trailing sep
        }
        else
            path.append(file.get(j));   // add file name

        return path.toString();
    }


    // FileUtil.copyFile() does not use transferTo() or sync()
    public static void copyFile(File src, File dst) throws IOException
    {
        dst.createNewFile();

        try (FileInputStream is = new FileInputStream(src); FileChannel in = is.getChannel();
             FileLock lockIn = in.lock(0L, Long.MAX_VALUE, true); FileOutputStream os = new FileOutputStream(dst);
             FileChannel out = os.getChannel(); FileLock lockOut = out.lock())
        {
            in.transferTo(0, in.size(), out);
            os.getFD().sync();
            dst.setLastModified(src.lastModified());
        }
    }

    /**
     * Copies an entire file system branch to another location, including the root directory itself
     * @param src The source file root
     * @param dest The destination file root
     * @throws IOException thrown from IO functions
     */
    public static void copyBranch(File src, File dest) throws IOException
    {
        copyBranch(src, dest, false);
    }

    /**
     * Copies an entire file system branch to another location
     *
     * @param src The source file root
     * @param dest The destination file root
     * @param contentsOnly Pass false to copy the root directory as well as the files within; true to just copy the contents
     * @throws IOException Thrown if there's an IO exception
     */
    public static void copyBranch(File src, File dest, boolean contentsOnly) throws IOException
    {
        //if src is just a file, copy it and return
        if(src.isFile())
        {
            File destFile = new File(dest, src.getName());
            copyFile(src, destFile);
            return;
        }

        //if copying the src root directory as well, make that
        //within the dest and re-assign dest to the new directory
        if(!contentsOnly)
        {
            dest = new File(dest, src.getName());
            dest.mkdirs();
            if(!dest.isDirectory())
                throw new IOException("Unable to create the directory " + dest.toString() + "!");
        }

        File[] children = src.listFiles();
        if (children == null)
        {
            throw new IOException("Unable to get file listing for directory: " + src);
        }
        for (File file : children)
        {
            copyBranch(file, dest, false);
        }
    }

    /**
     * always returns path starting with /.  Tries to leave trailing '/' as is
     * (unless ends with /. or /..)
     *
     * @param path path to normalize
     * @return cleaned path or null if path goes outside of 'root'
     */
    @Deprecated // use java.util.Path
    public static String normalize(String path)
    {
        if (path == null || equals(path,'/'))
            return path;

        String str = path;
        if (str.indexOf('\\') >= 0)
            str = str.replace('\\', '/');
        if (!startsWith(str,'/'))
            str = "/" + str;
        int len = str.length();

        // quick scan, look for /. or //
quickScan:
        {
            for (int i=0 ; i<len-1 ; i++)
            {
                char c0 = str.charAt(i);
                if (c0 != '/') continue;
                char c1 = str.charAt(i+1);
                if (c1 == '.' || c1 == '/')
                    break quickScan;
                i++;    //already looked at c1
            }
            return str;
        }

        ArrayList<String> list = normalizeSplit(str);
        if (null == list)
            return null;
        if (list.isEmpty())
            return "/";
        StringBuilder sb = new StringBuilder(str.length()+2);
        for (String name : list)
        {
            sb.append('/');
            sb.append(name);
        }
        return sb.toString();
    }


    @Deprecated // use java.util.Path
    public static ArrayList<String> normalizeSplit(String str)
    {
        int len = str.length();
        ArrayList<String> list = new ArrayList<>();
        int start = 0;
        for (int i=0 ; i<=len ; i++)
        {
            if (i==len || str.charAt(i) == '/')
            {
                if (start < i)
                {
                    String part = str.substring(start, i);
                    if (part.length()==0 || equals(part,'.'))
                    {
                    }
                    else if (part.equals(".."))
                    {
                        if (list.isEmpty())
                            return null;
                        list.remove(list.size()-1);
                    }
                    else
                    {
                        list.add(part);
                    }
                }
                start=i+1;
            }
        }
        return list;
    }


    public static String encodeForURL(String str)
    {
        // str is unencoded; we need certain special chars encoded for it to become a URL
        // % & # @ ~ {} []
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            if ('%' == str.charAt(i)) res.append("%25");
            else if ('#' == str.charAt(i)) res.append("%23");
            else if ('&' == str.charAt(i)) res.append("%26");
            else if ('@' == str.charAt(i)) res.append("%40");
            else if ('~' == str.charAt(i)) res.append("%7E");
            else if ('{' == str.charAt(i)) res.append("%7B");
            else if ('}' == str.charAt(i)) res.append("%7D");
            else if ('[' == str.charAt(i)) res.append("%5B");
            else if (']' == str.charAt(i)) res.append("%5D");
            else if ('+' == str.charAt(i)) res.append("%2B");
            else if (' ' == str.charAt(i)) res.append("%20");   // space also
            else res.append(str.charAt(i));
        }
        return res.toString();
    }

    static boolean startsWith(String s, char ch)
    {
        return s.length() > 0 && s.charAt(0) == ch;
    }

    static boolean endsWith(String s, char ch)
    {
        return s.length() > 0 && s.charAt(s.length()-1) == ch;
    }

    static boolean equals(String s, char ch)
    {
        return s.length() == 1 && s.charAt(0) == ch;
    }

    public static String relativePath(String dir, String filePath)
    {
        dir = normalize(dir);
        filePath = normalize(filePath);
        if (dir.endsWith("/"))
            dir = dir.substring(0,dir.length()-1);
        if (!filePath.toLowerCase().startsWith(dir.toLowerCase()))
            return null;
        String relPath = filePath.substring(dir.length());
        if (relPath.length() == 0)
            return relPath;
        if (relPath.startsWith("/"))
            return relPath.substring(1);
        return null;
    }


    private static String digest(MessageDigest md, InputStream is) throws IOException
    {
        try (DigestInputStream dis = new DigestInputStream(is, md))
        {
            byte[] buf = new byte[8 * 1024];
            while (-1 != (dis.read(buf)))
            {
                /* */
            }
            return Crypt.encodeHex(md.digest());
        }
    }

    public static String sha1sum(InputStream is) throws IOException
    {
        try
        {
            return digest(MessageDigest.getInstance("SHA1"), is);
        }
        catch (NoSuchAlgorithmException e)
        {
            LOG.error("unexpected error", e);
            return null;
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }

    public static String sha1sum(byte[] bytes) throws IOException
    {
        return sha1sum(new ByteArrayInputStream(bytes));
    }

    public static String sha1sum(File file) throws IOException
    {
        return sha1sum(new FileInputStream(file));
    }

    public static String md5sum(InputStream is) throws IOException
    {
        try
        {
            return digest(MessageDigest.getInstance("MD5"), is);
        }
        catch (NoSuchAlgorithmException e)
        {
            LOG.error("unexpected error", e);
            return null;
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }

    public static String md5sum(File file) throws IOException
    {
        return md5sum(new FileInputStream(file));
    }

    public static String md5sum(byte[] bytes) throws IOException
    {
        return md5sum(new ByteArrayInputStream(bytes));
    }


    public static byte[] readHeader(@NotNull File f, int len) throws IOException
    {
        try (InputStream is = new BufferedInputStream(new FileInputStream(f)))
        {
            return FileUtil.readHeader(is, len);
        }
    }

    public static byte[] readHeader(@NotNull InputStream is, int len) throws IOException
    {
        assert is.markSupported();
        is.mark(len);
        try
        {
            byte[] buf = new byte[len];
            while (0 < len)
            {
                int r = is.read(buf, buf.length-len, len);
                if (r == -1)
                {
                    byte[] ret = new byte[buf.length-len];
                    System.arraycopy(buf, 0, ret, 0, buf.length-len);
                    return ret;
                }
                len -= r;
            }
            return buf;
        }
        finally
        {
            is.reset();
        }
    }


    //
    //  NOTE: IOUtil uses fairly small buffers for copy
    //

    final static int BUFFERSIZE = 32*1024;

    // Closes input stream
    public static long copyData(InputStream is, File file) throws IOException
    {
        try (InputStream input = is; FileOutputStream fos = new FileOutputStream(file))
        {
            return copyData(input, fos);
        }
    }

    /** Does not close input or output stream */
    public static long copyData(InputStream is, OutputStream os) throws IOException
    {
        byte[] buf = new byte[BUFFERSIZE];
        long total = 0;
        int r;
        while (0 <= (r = is.read(buf)))
        {
            os.write(buf,0,r);
            total += r;
        }
        return total;
    }


    /** Does not close input or output stream */
    public static void copyData(InputStream is, DataOutput os, long len) throws IOException
    {
        byte[] buf = new byte[BUFFERSIZE];
        long remaining = len;
        do
        {
            int r = (int)Math.min(buf.length, remaining);
            r = is.read(buf, 0, r);
            os.write(buf,0,r);
            remaining -= r;
        } while (0 < remaining);
    }


    /** Does not close input or output stream */
    public static void copyData(InputStream is, DataOutput os) throws IOException
    {
        byte[] buf = new byte[BUFFERSIZE];
        int r;
        while (0 < (r = is.read(buf)))
            os.write(buf,0,r);
    }

    private static char[] illegalChars = {'/','\\',':','?','<','>','*','|','"','^'};

    public static boolean isLegalName(String name)
    {
        if (name == null || 0 == name.trim().length())
            return false;

        if (name.length() > 255)
            return false;

        return !StringUtils.containsAny(name, illegalChars);
    }

    public static String makeLegalName(String name)
    {
        //limit to 255 chars (FAT and OS X)
        //replace illegal chars
        //can't end with space (windows)
        //can't end with period (windows)
        char[] ret = new char[Math.min(255, name.length())];
        for(int idx = 0; idx < ret.length; ++idx)
        {
            char ch = name.charAt(idx);
            if(ch == '/' || ch == '?' || ch == '<' || ch == '>' || ch == '\\' || ch == ':'
                    || ch == '*' || ch == '|' || ch == '"' || ch == '^')
            {
                ch = '_';
            }

            if(idx == name.length() - 1 && (ch == ' ' || ch == '.'))
                ch = '_';

            ret[idx] = ch;
        }
        return new String(ret);
    }

    /**
     * Returns the absolute path to a file. On Windows and Mac, corrects casing in file paths to match the
     * canonical path.
     */
    public static File getAbsoluteCaseSensitiveFile(File file)
    {
        file = resolveFile(file.getAbsoluteFile());
        if (isCaseInsensitiveFileSystem())
        {
            try
            {
                @SuppressWarnings("SSBasedInspection")
                File canonicalFile = file.getCanonicalFile();

                if (canonicalFile.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath()))
                {
                    return canonicalFile;
                }
            }
            catch (IOException e)
            {
                // Ignore and just use the absolute file
            }
        }
        return file.getAbsoluteFile();
    }

    public static boolean isCaseInsensitiveFileSystem()
    {
        String osName = System.getProperty("os.name").toLowerCase();
        return (osName.startsWith("windows") || osName.startsWith("mac os"));
    }

    /**
     * Strips out ".." and "." from the path
     */
    public static File resolveFile(File file)
    {
        File parent = file.getParentFile();
        if (parent == null)
        {
            return file;
        }
        if (".".equals(file.getName()))
        {
            return resolveFile(parent);
        }
        int dotDotCount = 0;
        while ("..".equals(file.getName()) || dotDotCount > 0)
        {
            if ("..".equals(file.getName()))
            {
                dotDotCount++;
            }
            else if (!".".equals(file.getName()))
            {
                dotDotCount--;
            }
            if (parent.getParentFile() == null)
            {
                return parent;
            }
            file = file.getParentFile();
            parent = file.getParentFile();
        }
        return new File(resolveFile(parent), file.getName());
    }

    public static Path createTempDirectory(String prefix) throws IOException
    {
        return Files.createTempDirectory(prefix);
    }


    // Under Catalina, it seems to pick \tomcat\temp
    // On the web server under Tomcat, it seems to pick c:\Documents and Settings\ITOMCAT_EDI\Local Settings\Temp
    public static File getTempDirectory()
    {
        if (null == _tempDir)
        {
            try
            {
                File temp = File.createTempFile("deleteme", null);
                _tempDir = temp.getParentFile();
                temp.delete();
            }
            catch (IOException e)
            {
                throw new ConfigurationException("The temporary directory (likely " + System.getProperty("java.io.tmpdir") + ") on this server is inaccessible. There may be a file permission issue, or the directory may not exist.", e);
            }
        }

        return _tempDir;
    }


    // Converts a document name into keywords appropriate for indexing. We want to retrieve a document named "labkey.txt"
    // when the user searches for "labkey.txt", "labkey" or "txt". Lucene analyzers tokenize on whitespace, so this method
    // returns the original document name plus the document name with common symbols replaced with spaces.
    public static String getSearchKeywords(String documentName)
    {
        return documentName + " " + documentName.replaceAll("[._-]", " ");
    }


    /**
     * Creates a legal, cross-platform file name from the component parts (replacing special characters like colons, semi-colons, slashes, etc
     * @param prefix the start of the file name to generate, to be appended with a timestamp suffix
     * @param extension the extension (not including the dot) for the desired file name
     */
    public static String makeFileNameWithTimestamp(String prefix, String extension)
    {
        return makeLegalName(prefix + "_" + getTimestamp() + "." + extension);
    }

    public static String makeFileNameWithTimestamp(String prefix)
    {
        return makeLegalName(prefix + "_" + getTimestamp());
    }


    private static long lastTime = 0;
    private static final Object timeLock = new Object();

    // return a unique time, rounded to the nearest second
    private static long currentSeconds()
    {
        synchronized(timeLock)
        {
            long sec = HeartBeat.currentTimeMillis();
            sec -= sec % 1000;
            lastTime = Math.max(sec, lastTime + 1000);
            return lastTime;
        }
    }


    public static String getTimestamp()
    {
        String time = DateUtil.toISO(currentSeconds(), false);
        time = time.replace(":", "-");
        time = time.replace(" ", "_");

        return time;
    }

    private static String indent(LinkedList<Boolean> hasMoreFlags)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = hasMoreFlags.size(); i < len; i++)
        {
            Boolean hasMore = hasMoreFlags.get(i);
            if (i == len-1)
                sb.append(hasMore ? "├── " : "└── ");
            else
                sb.append(hasMore ? "│   " : "    ");
        }

        return sb.toString();
    }

    private static void printTree(StringBuilder sb, Path node, LinkedList<Boolean> hasMoreFlags) throws IOException
    {
        Files.walkFileTree(node, new SimplePathVisitor()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                hasMoreFlags.add(true);
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                appendFileLogEntry(sb, file, hasMoreFlags);
                return super.visitFile(file, attrs);
            }


            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
            {
                hasMoreFlags.removeLast();
                return super.postVisitDirectory(dir, exc);
            }
        });
    }

    private static void appendFileLogEntry(StringBuilder sb, Path node, LinkedList<Boolean> hasMoreFlags) throws IOException
    {
        if (hasMoreFlags.isEmpty())
            sb.append(node.toAbsolutePath());
        else
            sb.append(indent(hasMoreFlags)).append(node.getFileName());

        if (Files.isDirectory(node))
            sb.append("/");
        else
            sb.append(" (").append(FileUtils.byteCountToDisplaySize(Files.size(node))).append(")");
        sb.append("\n");
    }

    public static String printTree(Path root) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        printTree(sb, root, new LinkedList<>());
        return sb.toString();
    }

    public static String printTree(File root) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        printTree(sb, root.toPath(), new LinkedList<>());
        return sb.toString();
    }

    public static String getUnencodedAbsolutePath(Container container, Path path)
    {
        if (!path.isAbsolute())
            return null;
        else if (!FileUtil.hasCloudScheme(path))
            return path.toFile().getAbsolutePath();
        else
        {
            return PageFlowUtil.decode( //URI conversion encodes
                getPathStringWithoutAccessId(
                        CloudStoreService.get().getPathFromUrl(container, path.toString()).toUri()
                )
            );
        }
    }


    public static class TestCase extends Assert
    {
        private static final File ROOT;

        static
        {
            File f = new File(".").getAbsoluteFile();
            while (f.getParentFile() != null)
            {
                f = f.getParentFile();
            }
            ROOT = f;
        }

        @Test
        public void testStandardResolve()
        {
            assertEquals(new File(ROOT, "test/path/sub"), resolveFile(new File(ROOT, "test/path/sub")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "test")));
            assertEquals(new File(ROOT, "test/path/file.ext"), resolveFile(new File(ROOT, "test/path/file.ext")));
        }

        @Test
        public void testDotResolve()
        {
            assertEquals(new File(ROOT, "test/path/sub"), resolveFile(new File(ROOT, "test/path/./sub")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "./test")));
            assertEquals(new File(ROOT, "test/path/file.ext"), resolveFile(new File(ROOT, "test/path/file.ext/.")));
        }

        @Test
        public void testDotDotResolve()
        {
            assertEquals(ROOT, resolveFile(new File(ROOT, "..")));
            assertEquals(new File(ROOT, "test/sub"), resolveFile(new File(ROOT, "test/path/../sub")));
            assertEquals(new File(ROOT, "test/sub2"), resolveFile(new File(ROOT, "test/path/../sub/../sub2")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "test/path/sub/../..")));
            assertEquals(new File(ROOT, "sub"), resolveFile(new File(ROOT, "test/path/../../sub")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "test/path/../../sub/../sub2")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "test/path/.././../sub/../sub2")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "test/path/.././../sub/../../sub2")));
            assertEquals(new File(ROOT, "sub2"), resolveFile(new File(ROOT, "a/test/path/.././../sub/../../sub2")));
            assertEquals(new File(ROOT, "b/sub2"), resolveFile(new File(ROOT, "b/a/test/path/.././../sub/../../sub2")));
            assertEquals(ROOT, resolveFile(new File(ROOT, "test/path/../../../..")));
            assertEquals(new File(ROOT, "test/sub"), resolveFile(new File(ROOT, "../../../../test/sub")));
            assertEquals(new File(ROOT, "test"), resolveFile(new File(ROOT, "../test")));
            assertEquals(new File(ROOT, "test/path"), resolveFile(new File(ROOT, "test/path/file.ext/..")));
            assertEquals(new File(ROOT, "folder"), resolveFile(new File(ROOT, ".././../folder")));
            assertEquals(new File(ROOT, "b"), resolveFile(new File(ROOT, "folder/a/.././../b")));
        }

        @Test
        public void testUriToString()
        {
            assertEquals("converted file:/// URI does not match expected string", "file:///data/myfile.txt", uriToString(URI.create("file:///data/myfile.txt")));
            assertEquals("converted file:/ URI does not match expected string", "file:///data/myfile.txt", uriToString(URI.create("file:/data/myfile.txt")));
        }

        @Test
        public void testNormalizeURI()
        {
            assertEquals("file:/// uri not as expected","file:///my/triple/file/path", uriToString(URI.create("file:///my/triple/file/path")));
            assertEquals("file:/// uri with drive letter not as expected","file:///C:/my/triple/file/path", uriToString(URI.create("file:///C:/my/triple/file/path")));
            assertEquals("file:/ uri not conformed to file:///","file:///my/single/file/path", uriToString(URI.create("file:/my/single/file/path")));
            assertEquals("file:/ with drive letter not conformed to file:///","file:///C:/my/single/file/path", uriToString(URI.create("file:/C:/my/single/file/path")));
            assertEquals("File uri with host not as expected", "file://localhost:8080/my/host/file/path", uriToString(URI.create("file://localhost:8080/my/host/file/path")));
            assertEquals("Schemed URI not as expected","http://localhost:8080/my/triple/file/path?query=abcd#anchor", uriToString(URI.create("http://localhost:8080/my/triple/file/path?query=abcd#anchor")));
        }
    }
}
