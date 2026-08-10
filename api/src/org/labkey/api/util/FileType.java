/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.apache.commons.io.IOCase;
import org.apache.logging.log4j.Logger;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Captures a file naming convention via an ordered list of suffixes (usually extensions, but any name-ending works).
 * One is the canonical suffix used when creating new files. Optional constraints on MIME content type, directory-ness,
 * and file header contents.
 *
 * Matching ignores case unless {@link #setCaseSensitiveOnCaseSensitiveFileSystems} is set.
 *
 * Because suffixes match by name-ending, a broad type swallows names belonging to a more specific one — pepXML's ".xml"
 * also matches protXML's ".pep-prot.xml". Register the specific type via {@link #addAntiFileType} to exclude it.
 *
 * Subclasses distinguish types that share an extension (".txt", ".xml") by overriding {@link #isHeaderMatch}; the base
 * implementation never matches on header alone.
 */
public class FileType implements Serializable
{
    private static final Detector DETECTOR = new DefaultDetector(MimeTypes.getDefaultMimeTypes());
    private static final Logger LOG = LogHelper.getLogger(FileType.class, "file suffix matching");

    // For serialization
    protected FileType() {}
    
    /** Asks the job for a file under each suffix in priority order, falling back to {@link #getDefaultName} when none exist. */
    public FileLike findInputFile(FileAnalysisJobSupport support, String baseName)
    {
        if (_suffixes.size() > 1)
        {
            for (String suffix : _suffixes)
            {
                FileLike f = support.findInputFile(baseName + suffix);
                if (NetworkDrive.exists(f))
                {
                    return f;
                }
            }
        }

        return support.findInputFile(getDefaultName(baseName));
    }

    /** handle TPP's native use of .xml.gz **/
    public enum gzSupportLevel
    {
        NO_GZ,      // we don't support gzip for this filetype
        SUPPORT_GZ, // we support gzip for this filetype, but it's not the norm
        PREFER_GZ   // we support gzip for this filetype, and it's the default for new files
    }

    /** A list of possible suffixes in priority order. Later suffixes may also match earlier suffixes */
    private List<String> _suffixes;
    /** a list of filetypes to reject - handles the scenario where old pepxml files are "foo.xml" and
     * we have to avoid grabbing "foo.pep-prot.xml"
     */
    private List<FileType> _antiTypes;
    /** The canonical suffix, will be used when creating new files from scratch */
    private String _defaultSuffix;

    /** Mime content type. */
    private List<String> _contentTypes;
    
    private Boolean _dir;
    /** If _preferGZ is true, assume suffix.gz for new files to support TPP's transparent .xml.gz useage.
     * When dealing with existing files, non-gz version is still assumed to be the target if found **/
    private Boolean _preferGZ;
    /** If _supportGZ is true, accept .suffix.gz as the equivalent of .suffix **/ 
    private Boolean _supportGZ;
    private boolean _caseSensitiveOnCaseSensitiveFileSystems = false;

    /**
     * true if the different file extensions are just transformed versions of the same data (such as .raw and .mzXML)
     * and therefore if multiple are present only the first should be considered for actions in the UI.
     * false if they are independent and should all be considered actionable
     */
    private boolean _extensionsMutuallyExclusive = true;

    /**
     * Constructor to use when type is assumed to be a file, but a call to isDirectory()
     * is not necessary.
     *
     * @param supportGZ for handling of TPP's transparent use of .xml.gz
     * @param suffix usually the file extension, but may be some other suffix to
     *          uniquely identify a file type
     *
     */
    public FileType(String suffix, gzSupportLevel supportGZ)
    {
        this(Arrays.asList(suffix), suffix, supportGZ);
    }

    /**
     * Constructor to use when type is assumed to be a file, but a call to isDirectory()
     * is not necessary.
     *
     * @param suffix usually the file extension, but may be some other suffix to
     *          uniquely identify a file type
     *
     */
    public FileType(String suffix)
    {
        this(Arrays.asList(suffix), suffix);
    }

    /**
     * Constructor to use when a call to isDirectory() is necessary to differentiate this
     * file type.
     *
     * @param suffix usually the file extension, but may be some other suffix to
     *          uniquely identify a file type
     * @param dir true when the type must be a directory
     */
    public FileType(String suffix, boolean dir)
    {
        this(Arrays.asList(suffix), suffix, dir, gzSupportLevel.NO_GZ);
    }

    /**
     * @param suffixes list of what are usually the file extensions (but may be some other suffix to
     *          uniquely identify a file type), in priority order. The first suffix that matches a file will be used
     *          and files that match the rest of the suffixes will be ignored
     * @param defaultSuffix the canonical suffix, will be used when creating new files from scratch
     */
    public FileType(List<String> suffixes, String defaultSuffix)
    {
        this(suffixes, defaultSuffix, false, gzSupportLevel.NO_GZ);
    }

    /**
     * @param suffixes list of what are usually the file extensions (but may be some other suffix to
     *          uniquely identify a file type), in priority order. The first suffix that matches a file will be used
     *          and files that match the rest of the suffixes will be ignored
     * @param defaultSuffix the canonical suffix, will be used when creating new files from scratch
     * @param contentTypes Content types for this file type.  If null, a content type will be guessed based on the extension.
     */
    public FileType(List<String> suffixes, String defaultSuffix, List<String> contentTypes)
    {
        this(suffixes, defaultSuffix, false, gzSupportLevel.NO_GZ, contentTypes);
    }

    /**
     * @param suffixes list of what are usually the file extensions (but may be some other suffix to
     *          uniquely identify a file type), in priority order. The first suffix that matches a file will be used
     *          and files that match the rest of the suffixes will be ignored
     * @param defaultSuffix the canonical suffix, will be used when creating new files from scratch
     * @param dir true when the type must be a directory
     */
    public FileType(List<String> suffixes, String defaultSuffix, boolean dir)
    {
        this(suffixes, defaultSuffix, dir, gzSupportLevel.NO_GZ);
    }

    /**
     * @param suffixes list of what are usually the file extensions (but may be some other suffix to
     *          uniquely identify a file type), in priority order. The first suffix that matches a file will be used
     *          and files that match the rest of the suffixes will be ignored
     * @param defaultSuffix the canonical suffix, will be used when creating new files from scratch
     * @param dir true when the type must be a directory
     * @param supportGZ for handling TPP's transparent use of .xml.gz
     */
    public FileType(List<String> suffixes, String defaultSuffix, boolean dir, gzSupportLevel supportGZ)
    {
        this(suffixes, defaultSuffix, dir, supportGZ, null);
    }


    /**
     * @param suffixes list of what are usually the file extensions (but may be some other suffix to
     *          uniquely identify a file type), in priority order. The first suffix that matches a file will be used
     *          and files that match the rest of the suffixes will be ignored
     * @param defaultSuffix the canonical suffix, will be used when creating new files from scratch
     * @param doSupportGZ for handling TPP's transparent use of .xml.gz
     */
    public FileType(List<String> suffixes, String defaultSuffix, gzSupportLevel doSupportGZ)
    {
        this(suffixes, defaultSuffix, false, doSupportGZ, null);
    }

    /**
     * @param suffixes list of what are usually the file extensions (but may be some other suffix to
     *          uniquely identify a file type), in priority order. The first suffix that matches a file will be used
     *          and files that match the rest of the suffixes will be ignored
     * @param defaultSuffix the canonical suffix, will be used when creating new files from scratch
     * @param doSupportGZ for handling TPP's transparent use of .xml.gz
     * @param contentTypes Content types for this file type.  If null, a content type will be guessed based on the extension.
     */
    public FileType(List<String> suffixes, String defaultSuffix, boolean dir, gzSupportLevel doSupportGZ, List<String> contentTypes)
    {
        _suffixes = suffixes;
        supportGZ(doSupportGZ);
        _defaultSuffix = defaultSuffix;
        _dir = Boolean.valueOf(dir);
        _antiTypes = new ArrayList<>(0);
        if (!suffixes.contains(defaultSuffix))
        {
            throw new IllegalArgumentException("List of suffixes " + _suffixes + " does not contain the preferred suffix:" + _defaultSuffix);
        }

        if (contentTypes == null)
        {
            MimeMap mm = new MimeMap();
            String contentType = mm.getContentType(defaultSuffix);
            if (contentType != null)
                _contentTypes = Collections.singletonList(contentType);
            else
                _contentTypes = Collections.emptyList();
        }
        else
        {
            _contentTypes = Collections.unmodifiableList(new ArrayList<>(contentTypes));
        }
    }

    /** helper for supporting TPP's use of .xml.gz */
    private String tryName(Path parentDir, String name)
    {
        if (_supportGZ.booleanValue())  // TPP treats xml.gz as a native format
        {
            FileUtil.legalPathPartThrow(name);
            // in the case of existing files, non-gz copy wins if present
            Path f = parentDir!=null ? FileUtil.appendName(parentDir, name) : Path.of(name);
            if (!NetworkDrive.exists(f))
            {  // non-gz copy doesn't exist - how about .gz version?
                String gzname = name + ".gz";
                if (_preferGZ.booleanValue())
                {   // we like .gz for new filenames, so don't care if exists
                    return gzname;
                }
                f = parentDir!=null ? FileUtil.appendName(parentDir, gzname) : Path.of(gzname);
                if (NetworkDrive.exists(f))
                { // we don't prefer .gz, but we support it if it exists
                    return gzname;
                }
            }
        }
        return name;
    }

    /** Uses the preferred suffix, useful when there's not a directory of existing files to reference.
     *  if _preferGZ is set, will use preferred suffix.gz since TPP treats .gz as native format,
     *  unless non-gz file exists */
    public String getDefaultName(String basename)
    {
        return tryName(null, basename + _defaultSuffix);
    }

    /**
     * turn support for gzipped files on and off
     */
    public boolean supportGZ(gzSupportLevel doSupportGZ)
    {
        _supportGZ = Boolean.valueOf(doSupportGZ != gzSupportLevel.NO_GZ);
        _preferGZ = Boolean.valueOf(doSupportGZ == gzSupportLevel.PREFER_GZ);
        return _supportGZ.booleanValue();
    }

    /**
     * add a new supported suffix, return new list length
     */
    public int addSuffix(String newsuffix)
    {
        List<String> s = new ArrayList<>(_suffixes.size()+1);
        s.addAll(_suffixes);
        s.add(newsuffix);
        _suffixes = s;
        return _suffixes.size();
    }

    /**
     * add a new filetype to reject, return new list length
     */
    public int addAntiFileType(FileType anti)
    {
        List<FileType> s = new ArrayList<>(_antiTypes.size()+1);
        s.addAll(_antiTypes);
        s.add(anti);
        _antiTypes = s;
        return _antiTypes.size();
    }

    // used to avoid, for example, mistaking protxml ".pep-prot.xml" for pepxml ".xml" file 
    private boolean isAntiFileType(String name, byte[] header)
    {
        for (FileType a : _antiTypes)
        {
            if (a.isType(name))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Looks for a file in the parentDir that matches, in priority order. If one is found, returns its file name.
     * If nothing matches, uses the defaultSuffix to build a file name.
     */
    public String getName(FileLike parentDir, String basename)
    {
        return getName(parentDir.toNioPathForRead(), basename);
    }

    public String getName(Path parentDir, String basename)
    {
        if (_suffixes.size() > 1)
        {
            // Only bother checking if we have more than one possible suffix
            for (String suffix : _suffixes)
            {
                String name = tryName(parentDir, basename + suffix);
                Path f = FileUtil.appendName(parentDir, name);
                if (NetworkDrive.exists(f))
                {
                    // avoid, for example, mistaking protxml ".pep-prot.xml" for pepxml ".xml" file
                    if (!isAntiFileType(name, null))
                    {
                        return name;
                    }
                }
            }
        }
        return tryName(parentDir, basename + _defaultSuffix);
    }

    public String getName(String parentDirName, String basename)
    {
        return getName(new File(parentDirName).toPath(), basename);
    }

    /**
     * Looks for a file in the parentDir that matches, in priority order. If one is found, returns its file name.
     * If nothing matches, uses the defaultSuffix to build a file name.
     */
    public FileLike getFile(FileLike parentDir, String basename)
    {
        return parentDir.resolveChild(getName(parentDir, basename));
    }

    /**
     * @return the index of the first suffix that matches. Useful when looking through a directory of files and
     * determining which is the preferred file for this FileType.
     */
    public int getIndexMatch(Path file)
    {
        return getIndexMatch(file.getFileName().toString(), file.toString());
    }

    private int getIndexMatch(String filename, String filePath)
    {
        if (!isAntiFileType(filename, null))  // avoid, for example, mistaking .pep-prot.xml for .xml
        {
            for (int i = 0; i < _suffixes.size(); i++)
            {
                String s = toLowerIfCaseInsensitive(_suffixes.get(i));
                if (toLowerIfCaseInsensitive(filename).endsWith(s))
                {
                    return i;
                }
                // TPP treats .xml.gz as a native format
                if (_supportGZ.booleanValue() && toLowerIfCaseInsensitive(filename).endsWith(s + ".gz"))
                {
                    return i;
                }
            }
        }

        throw new IllegalArgumentException("No match found for " + filePath + " with " + this);
    }

    private String toLowerIfCaseInsensitive(String s)
    {
        if (s == null)
        {
            return null;
        }
        if (_caseSensitiveOnCaseSensitiveFileSystems && IOCase.SYSTEM.isCaseSensitive())
        {
            return s;
        }
        // ROOT, not the default locale: in Turkish 'I' lowercases to dotless 'ı', so ".MZID" would stop matching ".mzid"
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * Strips the longest matching suffix and returns the remainder, or the original file name if nothing matches. Longest
     * rather than first-in-list, so ".msprefix.mzXML" isn't reduced to "foo.msprefix".
     */
    public String getBaseName(File file)
    {
        return getBaseName(file.toPath());
    }

    public String getBaseName(FileLike file)
    {
        return getBaseName(file.toNioPathForRead());
    }

    public String getBaseName(@NotNull java.nio.file.Path file)
    {
        String fileName = file.getFileName().toString();
        if (isAntiFileType(fileName, null) || !isType(file))
            return fileName;

        String suffix = null;
        for (String s : _suffixes)
        {
            // run the entire list in order to assure strongest match
            // consider .msprefix.mzxml vs .mzxml for example
            if (toLowerIfCaseInsensitive(fileName).endsWith(toLowerIfCaseInsensitive(s)))
            {
                if ((null==suffix) || (s.length()>suffix.length()))
                {
                    suffix = s;
                }
            }
            else if (_supportGZ.booleanValue()) // TPP treats .xml.gz as a native read format
            {
                String sgz = s+".gz";
                if (toLowerIfCaseInsensitive(fileName).endsWith(toLowerIfCaseInsensitive(sgz)))
                {
                    if ((null==suffix) || (sgz.length()>suffix.length()))
                    {
                        suffix = sgz;
                    }
                }
            }
        }
        if (suffix == null)
        {
            // Unreachable unless isType() and this loop disagree; warn so it stays visible with assertions disabled
            String message = "Could not find matching suffix for " + fileName + " even though types match: " + this + ", supportGZ: " + _supportGZ;
            assert false : message;
            LOG.warn(message);
            return fileName;
        }
        return fileName.substring(0, fileName.length() - suffix.length());
    }

    public FileLike newFile(FileLike parent, String basename)
    {
        return parent.resolveChild(getName(parent, basename));
    }

    public Path newFile(Path parent, String basename)
    {
        return FileUtil.appendName(parent, getName(parent, basename));
    }

    public boolean isType(File file)
    {
        return isType(file, null, null);
    }

    public boolean isType(FileLike file)
    {
        return isType(file.toNioPathForRead(), null, null);
    }

    public boolean isType(java.nio.file.Path path)
    {
        return isType(path, null, null);
    }

    public boolean isType(java.nio.file.Path path, String contentType, byte[] header)
    {
        if ((path == null) || (_dir != null && _dir.booleanValue() != Files.isDirectory(path)))
            return false;

        return isType(path.getFileName().toString(), contentType, header);
    }


    public boolean isType(File file, String contentType, byte[] header)
    {
        if ((file == null) || (_dir != null && _dir.booleanValue() != file.isDirectory()))
            return false;

        return isType(file.getName(), contentType, header);
    }

    /**
     * Checks if the path matches any of the suffixes 
     */
    public boolean isType(String filePath)
    {
        return isType(filePath, null, null);
    }

    /**
     * Matches in order: reject if an anti-type matches, accept on content type (detected from the header via Tika when not
     * supplied), accept on suffix (with the header, if given) and finally accept on header alone, that last only when
     * the caller supplied no content type, since a caller-supplied type that didn't match is authoritative.
     */
    public boolean isType(@Nullable String filePath, @Nullable String contentType, byte @Nullable[] header)
    {
        String providedContentType = contentType; // Save it for later

        // avoid, for example, mistaking protxml ".pep-prot.xml" for pepxml ".xml"
        if (isAntiFileType(filePath, header))
        {
            return false;
        }

        // Attempt to match by content type.
        if (_contentTypes != null)
        {
            // Use Tika to determine the content type
            if (contentType == null && header != null)
                contentType = detectContentType(filePath, header);

            if (contentType != null)
            {
                contentType = contentType.toLowerCase(Locale.ROOT).trim();
                if (_contentTypes.contains(contentType))
                    return true;
            }
        }

        // Attempt to match by suffix and header.
        if (filePath != null)
        {
            filePath = toLowerIfCaseInsensitive(filePath);
            for (String suffix : _suffixes)
            {
                suffix = toLowerIfCaseInsensitive(suffix);
                if (filePath.endsWith(suffix))
                {
                    if (header == null || isHeaderMatch(header))
                        return true;
                }
                // TPP treats .xml.gz as a native format
                if (_supportGZ.booleanValue() && filePath.endsWith(suffix + ".gz"))
                {
                    if (header == null || isHeaderMatch(header))
                        return true;
                }
            }
        }

        // Attempt to match using just the header, but only if the original content type was null, Issue 47814
        return null == providedContentType && header != null && isHeaderMatch(header);
    }

    protected static String detectContentType(String fileName, byte[] header)
    {
        final Metadata metadata = new Metadata();
        metadata.set("resourceName", fileName);
        try (TikaInputStream is = TikaInputStream.get(header, metadata))
        {
            MediaType mediaType = DETECTOR.detect(is, metadata);
            if (mediaType != null)
                return mediaType.toString();

            return null;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Whether the name is exactly basename plus one of the suffixes, unlike {@link #isType}, which accepts any name ending in a suffix. */
    public boolean isMatch(String name, String basename)
    {
        String normalizedName = toLowerIfCaseInsensitive(name);
        for (String suffix : _suffixes)
        {
            String normalizedBase = toLowerIfCaseInsensitive(basename + suffix);
            if (normalizedName.equals(normalizedBase))
            {
                return true;
            }
            // TPP treats .xml.gz as a native format
            if (_supportGZ.booleanValue() && normalizedName.equals(normalizedBase + ".gz"))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the file header matches. This is useful for FileTypes that share an
     * extension, e.g. "txt" or "xml", or when the filename or extension isn't available.
     *
     * @param header First few K of the file.
     * @return True if the header matches, false otherwise.
     */
    public boolean isHeaderMatch(byte @NotNull [] header)
    {
        return false;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileType fileType = (FileType) o;

        if (!Objects.equals(_supportGZ, fileType._supportGZ)) return false;
        if (!Objects.equals(_preferGZ, fileType._preferGZ)) return false;
        if (!Objects.equals(_dir, fileType._dir)) return false;
        if (!Objects.equals(_defaultSuffix, fileType._defaultSuffix))
            return false;
        if (!Objects.equals(_antiTypes, fileType._antiTypes)) return false;
        return Objects.equals(_suffixes, fileType._suffixes);
    }

    public String getDefaultSuffix()
    {
        return _defaultSuffix;
    }

    public List<String> getSuffixes()
    {
        return Collections.unmodifiableList(_suffixes);
    }

    public int hashCode()
    {
        int result;
        result = (_suffixes != null ? _suffixes.hashCode() : 0);
        result = 31 * result + (_defaultSuffix != null ? _defaultSuffix.hashCode() : 0);
        result = 31 * result + (_dir != null ? _dir.hashCode() : 0);
        result = 31 * result + (_supportGZ != null ? _supportGZ.hashCode() : 0);
        result = 31 * result + (_preferGZ != null ? _preferGZ.hashCode() : 0);
        return result;
    }

    public String toString()
    {
        return (_dir == null || !_dir.booleanValue() ? _suffixes.toString() : _suffixes + "/");
    }

    /** The subset of types that at least one of the files matches, in the order given by {@code types}. */
    @NotNull
    public static List<FileType> findTypes(@NotNull List<FileType> types, @NotNull List<FileLike> files)
    {
        ArrayList<FileType> foundTypes = new ArrayList<>();
        // This O(n*m), but these are usually very short lists.
        for (FileType type : types)
        {
            for (FileLike file : files)
            {
                if (type.isType(file.getName()))
                {
                    foundTypes.add(type);
                    break;
                }
            }
        }
        return foundTypes;
    }

    /**
     * true if the different file extensions are just transformed versions of the same data (such as .raw and .mzXML)
     * and therefore if multiple are present only the first should be considered for actions in the UI.
     * false if they are independent and should all be considered actionable
     */
    public boolean isExtensionsMutuallyExclusive()
    {
        return _extensionsMutuallyExclusive;
    }

    /**
     * @param extensionsMutuallyExclusive true if the different file extensions are just transformed versions of the
     * same data (such as .raw and .mzXML) and therefore if multiple are present only the first should be
     * considered for actions in the UI.
     * false if they are independent and should all be considered actionable
     */
    public void setExtensionsMutuallyExclusive(boolean extensionsMutuallyExclusive)
    {
        _extensionsMutuallyExclusive = extensionsMutuallyExclusive;
    }

    /** The default suffix with everything through the first dot removed (".pep.xml" yields "pep.xml"); used as a pipeline input/output role name. */
    public String getDefaultRole()
    {
        if (_defaultSuffix.contains("."))
        {
            return _defaultSuffix.substring(_defaultSuffix.indexOf(".") + 1);
        }
        return _defaultSuffix;
    }

    public boolean isCaseSensitiveOnCaseSensitiveFileSystems()
    {
        return _caseSensitiveOnCaseSensitiveFileSystems;
    }

    /** Opt out of the default case-insensitive suffix matching, deferring to the file system: on a case-insensitive one (Windows, default macOS) matching stays case-insensitive. */
    public void setCaseSensitiveOnCaseSensitiveFileSystems(boolean caseSensitiveOnCaseSensitiveFileSystems)
    {
        _caseSensitiveOnCaseSensitiveFileSystems = caseSensitiveOnCaseSensitiveFileSystems;
    }

    public List<String> getContentTypes()
    {
        return _contentTypes;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // simple case
            FileType ft = new FileType(".foo");
            assertTrue(ft.isType("test.foo"));
            assertFalse(ft.isType("test.foo.gz"));
            assertEquals("test.foo",ft.getDefaultName("test"));

            // support for .gz
            FileType ftgz = new FileType(".foo",gzSupportLevel.SUPPORT_GZ);
            assertTrue(ftgz.isType("test.foo"));
            assertTrue(ftgz.isType("test.foo.gz"));
            assertEquals("test.foo",ftgz.getDefaultName("test"));

            // preference for .gz
            FileType ftgzgz = new FileType(".foo",gzSupportLevel.PREFER_GZ);
            assertTrue(ftgzgz.isType("test.foo"));
            assertTrue(ftgzgz.isType("test.foo.gz"));
            assertEquals("test.foo.gz",ftgzgz.getDefaultName("test"));

            // multiple extensions
            ArrayList<String> foobar = new ArrayList<>();
            foobar.add(".foo");
            foobar.add(".bar");
            FileType ftt = new FileType(foobar,".foo",false,gzSupportLevel.SUPPORT_GZ);
            assertTrue(ftt.isType("test.foo"));
            assertTrue(ftt.isType("test.bar"));
            assertTrue(ftt.isType("test.foo.gz"));
            assertTrue(ftt.isType("test.bar.gz"));
            assertTrue(ftt.isType("test.bAr.gZ")); // extensions are case insensitive
            assertEquals("test.foo",ftt.getDefaultName("test"));

            // antitypes - for example avoid mistaking protxml ".pep-prot.xml" for pepxml ".xml"
            assertTrue(ftt.isType("test.foo.bar"));
            ftt.addAntiFileType(new FileType(".foo.bar"));
            assertFalse(ftt.isType("test.foo.bar"));
            assertTrue(ftt.isType("test.foo"));
            assertTrue(ftt.isType("test.bar"));
        }

        @Test
        public void testCaseInsensitiveBaseName()
        {
            FileType ft = new FileType(Arrays.asList(".foo", ".bar"), ".foo", gzSupportLevel.SUPPORT_GZ);

            // getBaseName() must handle everything isType() accepts, including mixed case with .gz
            assertEquals("test", ft.getBaseName(Path.of("test.foo")));
            assertEquals("test", ft.getBaseName(Path.of("test.FOO")));
            assertEquals("test", ft.getBaseName(Path.of("test.foo.gz")));
            assertEquals("test", ft.getBaseName(Path.of("test.FOO.gz")));
            assertEquals("test", ft.getBaseName(Path.of("test.bAr.gZ")));

            // strongest match wins regardless of case
            FileType ftPrefix = new FileType(Arrays.asList(".mzxml", ".msprefix.mzxml"), ".mzxml", gzSupportLevel.SUPPORT_GZ);
            assertEquals("test", ftPrefix.getBaseName(Path.of("test.MSPREFIX.mzXML")));
            assertEquals("test", ftPrefix.getBaseName(Path.of("test.MSPREFIX.mzXML.GZ")));

            // the suffix itself may be mixed case, as massSpecDataFileType's ".mzXML" is
            FileType ftMixedSuffix = new FileType(".mzXML", gzSupportLevel.SUPPORT_GZ);
            assertEquals("test", ftMixedSuffix.getBaseName(Path.of("test.mzXML")));
            assertEquals("test", ftMixedSuffix.getBaseName(Path.of("test.mzxml")));
            assertEquals("test", ftMixedSuffix.getBaseName(Path.of("test.mzxml.gz")));
            assertEquals("test", ftMixedSuffix.getBaseName(Path.of("test.MZXML.GZ")));
            assertTrue(ftMixedSuffix.isMatch("test.mzxml.gz", "test"));

            // no match at all still returns the original file name
            assertEquals("test.unrelated", ft.getBaseName(Path.of("test.unrelated")));

            // isMatch() follows the same case rules as isType(), for both the plain and the .gz forms
            assertTrue(ft.isMatch("test.FOO", "test"));
            assertTrue(ft.isMatch("test.FOO.gz", "test"));
            assertTrue(ft.isMatch("test.bAr.gZ", "test"));
            assertFalse(ft.isMatch("test.unrelated", "test"));

            // A type that opts into case sensitivity still agrees with itself on either kind of file system:
            // isType(), getBaseName() and isMatch() all accept the file, or none of them do
            FileType ftCase = new FileType(".foo", gzSupportLevel.SUPPORT_GZ);
            ftCase.setCaseSensitiveOnCaseSensitiveFileSystems(true);
            String mixedCase = "test.FOO.gz";
            boolean accepted = ftCase.isType(mixedCase);
            assertEquals(accepted ? "test" : mixedCase, ftCase.getBaseName(Path.of(mixedCase)));
            assertEquals(accepted, ftCase.isMatch(mixedCase, "test"));
            assertTrue(ftCase.isMatch("test.foo.gz", "test"));
        }
    }
}
