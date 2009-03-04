/*
 * Copyright (c) 2009 LabKey Corporation
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

/*
* User: Dave
* Date: Mar 4, 2009
* Time: 11:41:14 AM
*/

/**
 * Parses a string-based version number into major, minor and revision numbers
 * based on the format "major.minor.revision". Revision can be either an integer
 * or a string.
 */
public class VersionNumber
{
    private int _major = 0;
    private int _minor = 0;
    private Object _revision = null;

    public VersionNumber(String version)
    {
        if(null == version || version.length() == 0)
            throw new RuntimeException("Null or empty version number string!");
        String versionParts[] = version.split("\\.");
        if(versionParts.length == 0)
            throw new RuntimeException("Invalid version number string ('" + version + "')");

        _major = Integer.parseInt(versionParts[0]);
        if(versionParts.length > 1)
            _minor = Integer.parseInt(versionParts[1]);
        if(versionParts.length > 2)
        {
            //try to parse as int, otherwise set as string
            try
            {
                _revision = Integer.valueOf(versionParts[2]);
            }
            catch(NumberFormatException e)
            {
                _revision = versionParts[2];
            }
        }
    }

    public VersionNumber(int major, int minor)
    {
        this(major, minor, null);
    }

    public VersionNumber(int major, int minor, int revision)
    {
        _major = major;
        _minor = minor;
        _revision = new Integer(revision);
    }

    public VersionNumber(int major, int minor, String revision)
    {
        _major = major;
        _minor = minor;
        _revision = revision;
    }

    public int getMajor()
    {
        return _major;
    }

    public int getMinor()
    {
        return _minor;
    }

    public Object getRevision()
    {
        return _revision;
    }

    @Override
    public String toString()
    {
        if(null != _revision)
            return _major + "." + _minor + "." + _revision.toString();
        else
            return _major + "." + _minor;
    }
}