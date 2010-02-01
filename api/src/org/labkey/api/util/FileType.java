/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.gwt.client.util.StringUtils;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Vector;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * <code>FileType</code>
 *
 * @author brendanx
 */
public class FileType implements Serializable
{
    /** handle TPP's native use of .xml.gz **/
    public static enum gzSupportLevel
    {
        NO_GZ,      // we don't support gzip for this filetype
        SUPPORT_GZ, // we support gzip for this filetype, but it's not the norm
        PREFER_GZ   // we support gzip for this filetype, and it's the default for new files
    };

    public static gzSupportLevel systemPreferenceGZ()
    {   // return PREFER_GZ iff .pep.xml.gz is preferred as
        // indicated by the env varbl TPP itself uses, else
        // return SUPPORT_GZ, since you still might get those
        // from outside sources
        String pepXMLext = StringUtils.trimToEmpty(System.getenv("PEPXML_EXT"));
        return pepXMLext.endsWith(".pep.xml.gz")? gzSupportLevel.PREFER_GZ: gzSupportLevel.SUPPORT_GZ;
    }

    /** A list of possible suffixes in priority order. Later suffixes may also match earlier suffixes */
    private List<String> _suffixes;
    /** The canonical suffix, will be used when creating new files from scratch */
    private String _defaultSuffix;
    private Boolean _dir;
    /** If _preferGZ is true, assume suffix.gz for new files to support TPP's transparent .xml.gz useage.
     * When dealing with existing files, non-gz version is still assumed to be the target if found **/
    private Boolean _preferGZ;
    /** If _supportGZ is true, accept .suffix.gz as the equivalent of .suffix **/ 
    private Boolean _supportGZ;

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
        * @param dir true when the type must be a directory
        * @param supportGZ for handling TPP's transparent use of .xml.gz
        */
       public FileType(List<String> suffixes, String defaultSuffix, boolean dir, gzSupportLevel supportGZ)
       {
           this(suffixes, defaultSuffix, supportGZ);
           _dir = Boolean.valueOf(dir);
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
     * @param doSupportGZ for handling TPP's transparent use of .xml.gz
     */
    public FileType(List<String> suffixes, String defaultSuffix, gzSupportLevel doSupportGZ)
    {
        _suffixes = suffixes;
        supportGZ(doSupportGZ);
        _defaultSuffix = defaultSuffix;
        if (!suffixes.contains(defaultSuffix))
        {
            throw new IllegalArgumentException("List of suffixes " + _suffixes + " does not contain the preferred suffix:" + _defaultSuffix);
        }
    }

    /** helper for supporting TPP's use of .xml.gz */
    private String tryName(File parentDir, String name)
    {
        if (_supportGZ.booleanValue())  // TPP treats xml.gz as a native format
        {   // in the case of existing files, non-gz copy wins if present
            File f = parentDir!=null ? new File(parentDir,name) : new File(name);
            if (!NetworkDrive.exists(f))
            {  // non-gz copy doesn't exist - how about .gz version?
                String gzname = name + ".gz";
                if (_preferGZ.booleanValue())
                {   // we like .gz for new filenames, so don't care if exists
                    return gzname;
                }
                f = parentDir!=null ? new File(parentDir,gzname) : new File(gzname);
                if (NetworkDrive.exists(f))
                { // we don't prefer .gz, but we support it if it exists
                    return gzname;
                }
            }
        }
        return name;
    }

    /** Uses the preferred suffix, useful when there's not a directory of existing files to reference */
    /** if _preferGZ is set, will use preferred suffix.gz since TPP treats .gz as native format,
     *  unless non-gz file exists */
    public String getName(String basename)
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
        Vector<String> s = new Vector<String>(_suffixes.size()+1);
        for (String suffix : _suffixes)
        {
            s.add(suffix);
        }
        s.add(newsuffix);
        _suffixes = s;
        return _suffixes.size();
    }

    /**
     * Looks for a file in the parentDir that matches, in priority order. If one is found, returns its file name.
     * If nothing matches, uses the defaultSuffix to build a file name.
     */
    public String getName(File parentDir, String basename)
    {
        if (_suffixes.size() > 1)
        {
            // Only bother checking if we have more than one possible suffix
            for (String suffix : _suffixes)
            {
                String name = tryName(parentDir, basename + suffix);
                File f = new File(parentDir, name);
                if (NetworkDrive.exists(f))
                {
                    return name;
                }
            }
        }
        return tryName(parentDir, basename + _defaultSuffix);
    }

    /**
     * Looks for a file in the parentDir that matches, in priority order. If one is found, returns its file name.
     * If nothing matches, uses the defaultSuffix to build a file name.
     */
    public File getFile(File parentDir, String basename)
    {
        return new File(parentDir, getName(parentDir, basename));
    }

    /**
     * @return the index of the first suffix that matches. Useful when looking through a directory of files and
     * determining which is the preferred file for this FileType.
     */
    public int getIndexMatch(File file)
    {
        for (int i = 0; i < _suffixes.size(); i++)
        {
            if (file.getName().endsWith(_suffixes.get(i)))
            {
                return i;
            }
            // TPP treats .xml.gz as a native format
            if (_supportGZ.booleanValue() && file.getName().endsWith(_suffixes.get(i)+".gz"))
            {
                return i;
            }
        }

        throw new IllegalArgumentException("No match found for " + file + " with " + toString());
    }

    /**
     * Finds the best suffix based on priority order, strips it off, and returns the remainder. If there is no matching
     * suffix, returns the original file name.
     */
    public String getBaseName(File file)
    {
        if (!isType(file))
            return file.getName();
        String suffix = null;
        for (String s : _suffixes)
        {
            if (file.getName().toLowerCase().endsWith(s.toLowerCase()))
            {
                suffix = s;
                break;
            }
            if (_supportGZ.booleanValue()) // TPP treats .xml.gz as a native read format
            {
                String sgz = s+".gz";
                if (file.getName().endsWith(sgz))
                {
                    suffix = sgz;
                    break;
                }
            }
        }
        assert suffix != null : "Could not find matching suffix even though types match";
        return file.getName().substring(0, file.getName().length() - suffix.length());
    }

    public File newFile(File parent, String basename)
    {
        return new File(parent, getName(parent, basename));
    }

    public boolean isType(File file)
    {
        if (_dir != null && _dir.booleanValue() != file.isDirectory())
            return false;
        
        return isType(file.getName());
    }

    /**
     * Checks if the path matches any of the suffixes 
     */
    public boolean isType(String filePath)
    {
        for (String suffix : _suffixes)
        {
            if (filePath.toLowerCase().endsWith(suffix.toLowerCase()))
            {
                return true;
            }
            // TPP treats .xml.gz as a native format
            if (_supportGZ.booleanValue() && filePath.endsWith(suffix+".gz"))
            {
                return true;
            }
        }
        return false;
    }

    public boolean isMatch(String name, String basename)
    {
        for (String suffix : _suffixes)
        {
            if (name.equalsIgnoreCase(basename + suffix))
            {
                return true;
            }
            // TPP treats .xml.gz as a native format
            if (_supportGZ.booleanValue() && name.equals(basename + suffix+".gz"))
            {
                return true;
            }
        }
        return false;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileType fileType = (FileType) o;

        if (_supportGZ != null ? !_supportGZ.equals(fileType._supportGZ) : fileType._supportGZ != null) return false;
        if (_preferGZ != null ? !_preferGZ.equals(fileType._preferGZ) : fileType._preferGZ != null) return false;
        if (_dir != null ? !_dir.equals(fileType._dir) : fileType._dir != null) return false;
        if (_defaultSuffix != null ? !_defaultSuffix.equals(fileType._defaultSuffix) : fileType._defaultSuffix != null)
            return false;
        return !(_suffixes != null ? !_suffixes.equals(fileType._suffixes) : fileType._suffixes != null);
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

    public static FileType[] findTypes(FileType[] types, File[] files)
    {
        ArrayList<FileType> foundTypes = new ArrayList<FileType>();
        // This O(n*m), but these are usually very short lists.
        for (FileType type : types)
        {
            for (File file : files)
            {
                if (type.isType(file))
                {
                    foundTypes.add(type);
                    break;
                }
            }
        }
        return foundTypes.toArray(new FileType[foundTypes.size()]);
    }

    /**
     * @return a FileType that will only match on the default suffix for this FileType
     */
    public FileType getDefaultFileType()
    {
        if (_suffixes.size() > 0)
        {
            FileType ft = new FileType(_defaultSuffix);
            ft._supportGZ = _supportGZ.booleanValue();
            ft._preferGZ = _preferGZ.booleanValue();
            return ft;
        }
        else
        {
            return this;
        }
    }

    public String getDefaultRole()
    {
        if (_defaultSuffix.contains("."))
        {
            return _defaultSuffix.substring(_defaultSuffix.indexOf(".") + 1);
        }
        return _defaultSuffix;
    }

    public static class TestCase extends junit.framework.TestCase
    {

        public void test()
        {
            // simple case
            FileType ft = new FileType(".foo");
            assertTrue(ft.isType("test.foo"));
            assertTrue(!ft.isType("test.foo.gz"));
            assertEquals("test.foo",ft.getName("test"));
            // support for .gz
            FileType ftgz = new FileType(".foo",gzSupportLevel.SUPPORT_GZ);
            assertTrue(ftgz.isType("test.foo"));
            assertTrue(ftgz.isType("test.foo.gz"));
            assertEquals("test.foo",ftgz.getName("test"));
            // preference for .gz
            FileType ftgzgz = new FileType(".foo",gzSupportLevel.PREFER_GZ);
            assertTrue(ftgzgz.isType("test.foo"));
            assertTrue(ftgzgz.isType("test.foo.gz"));
            assertEquals("test.foo.gz",ftgzgz.getName("test"));
            // multiple extensions
            ArrayList<String> foobar = new ArrayList<String>();
            foobar.add(".foo");
            foobar.add(".bar");
            FileType ftt = new FileType(foobar,".foo",false,gzSupportLevel.SUPPORT_GZ);
            assertTrue(ftt.isType("test.foo"));
            assertTrue(ftt.isType("test.bar"));
            assertTrue(ftt.isType("test.foo.gz"));
            assertTrue(ftt.isType("test.bar.gz"));
            assertEquals("test.foo",ftt.getName("test"));
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }


}
