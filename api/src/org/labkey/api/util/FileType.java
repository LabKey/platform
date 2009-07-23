/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * <code>FileType</code>
 *
 * @author brendanx
 */
public class FileType implements Serializable
{
    /** A list of possible suffixes in priority order. Later suffixes may also match earlier suffixes */
    private List<String> _suffixes;
    /** The canonical suffix, will be used when creating new files from scratch */
    private String _defaultSuffix;
    private Boolean _dir;

    /**
     * Constructor to use when type is assumed to be a file, but a call to isDirectory()
     * is not necessary.
     *
     * @param suffix usually the file extension, but may be some other suffix to
     *          uniquely identify a file type
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
        this(Arrays.asList(suffix), suffix, dir);
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
        this(suffixes, defaultSuffix);
        _dir = Boolean.valueOf(dir);
    }
    
    /**
     * @param suffixes list of what are usually the file extensions (but may be some other suffix to
     *          uniquely identify a file type), in priority order. The first suffix that matches a file will be used
     *          and files that match the rest of the suffixes will be ignored
     * @param defaultSuffix the canonical suffix, will be used when creating new files from scratch
     */
    public FileType(List<String> suffixes, String defaultSuffix)
    {
        _suffixes = suffixes;
        _defaultSuffix = defaultSuffix;
        if (!suffixes.contains(defaultSuffix))
        {
            throw new IllegalArgumentException("List of suffixes " + _suffixes + " does not contain the preferred suffix:" + _defaultSuffix);
        }
    }

    /** Uses the preferred suffix, useful when there's not a directory of existing files to reference */
    public String getName(String basename)
    {
        return basename + _defaultSuffix;
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
                File f = new File(parentDir, basename + suffix);
                if (NetworkDrive.exists(f))
                {
                    return f.getName();
                }
            }
        }
        return basename + _defaultSuffix;
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
        int n = 0;
        String suffix = null;
        for (String s : _suffixes)
        {
            if (file.getName().endsWith(s))
            {
                suffix = s;
                break;
            }
        }
        assert suffix != null : "Could not find matching suffix even though types match";
        for (int i = suffix.indexOf('.'); i >= 0; i = suffix.indexOf('.', i+1))
            n++;
        return FileUtil.getBaseName(file, n);
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
        // TODO: lowercase?
        for (String suffix : _suffixes)
        {
            if (filePath.endsWith(suffix))
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
            if (name.equals(basename + suffix))
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
            return new FileType(_defaultSuffix);
        }
        else
        {
            return this;
        }
    }

    public String getDefaultRole()
    {
        if (_defaultSuffix.indexOf(".") != -1)
        {
            return _defaultSuffix.substring(_defaultSuffix.indexOf(".") + 1);
        }
        return _defaultSuffix;
    }
}
