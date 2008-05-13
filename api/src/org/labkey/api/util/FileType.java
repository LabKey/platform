/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

/**
 * <code>FileType</code>
 *
 * @author brendanx
 */
public class FileType implements Serializable
{
    private String _suffix;

    /** @param suffix usually the file extension, but may be some other suffix to uniquely identify a file type */
    public FileType(String suffix)
    {
//        assert suffix.charAt(0) == '.' : "Extension must begin with a period.";
        _suffix = suffix;
    }

    public String getSuffix()
    {
        return _suffix;
    }

    public String getName(String basename)
    {
        return basename + _suffix;
    }

    public String getBaseName(File file)
    {
        if (!isType(file))
            return file.getName();
        int n = 0;
        for (int i = _suffix.indexOf('.'); i >= 0; i = _suffix.indexOf('.', i+1))
            n++;
        return FileUtil.getBaseName(file, n);
    }

    public File newFile(File parent, String basename)
    {
        return new File(parent, getName(basename));
    }

    public boolean isType(File file)
    {
        return isType(file.getName());
    }

    public boolean isType(String filePath)
    {
        // TODO: lowercase?
        return filePath.endsWith(_suffix);
    }

    public boolean isMatch(String name, String basename)
    {
        return name.equals(basename + _suffix);
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileType fileType = (FileType) o;

        if (!_suffix.equals(fileType._suffix)) return false;

        return true;
    }

    public int hashCode()
    {
        return _suffix.hashCode();
    }
}
