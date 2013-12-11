/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import java.io.Serializable;

/**
 * Parses a string-based version number into major, minor and revision numbers
 * based on the format "major.minor.revision". Revision can be either an integer
 * or a string.
 */
public class VersionNumber implements Serializable
{
    private int _major = 0;
    private int _minor = 0;
    private Object _revision = null;

    public VersionNumber(String version)
    {
        if (null == version || version.length() == 0)
            throw new RuntimeException("Null or empty version number string!");

        String versionParts[] = version.split("\\.");

        if (versionParts.length == 0)
            throw new RuntimeException("Invalid version number string ('" + version + "')");

        _major = Integer.parseInt(versionParts[0]);

        if (versionParts.length > 1)
        {
            // Lenient int parser that allows non-digit characters after the number portion.  This fixes PostgreSQL 8.4 Beta 1
            //  which returns "4beta1" for minor version. 
            String minorString = versionParts[1];
            int i;

            for (i = 0; i < minorString.length(); i++)
                if (!Character.isDigit(minorString.charAt(i)))
                    break;

            _minor = Integer.parseInt(versionParts[1].substring(0, i));
        }

        if (versionParts.length > 2)
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
        _revision = revision;
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

    public int getRevisionAsInt()
    {
        if (null != _revision && _revision instanceof Integer)
            return (Integer)_revision;
        else
            return 0;
    }

    @Override
    public String toString()
    {
        if (null != _revision)
            return _major + "." + _minor + "." + _revision.toString();
        else
            return _major + "." + _minor;
    }

    //
    // Returns major & minor version numbers (but not revision) in an int form that's easier for range checking:
    //
    //      major * 10 + minor
    //
    // Examples:
    //
    //      8.3 --> 83
    //      8.4 --> 84
    //      9.0 --> 90
    //
    // Requires (and validates that) minor version is a single-digit (0 <= minor <= 9)
    //
    public int getVersionInt()
    {
        // Temporary fix for SQL Server 2008 R2 (10.50.1600.1).  TODO: Support two-digit version ints (MMmm) 
        if (_minor > 9)
            _minor = _minor / 10;
        if (_minor > 9 || _minor < 0)
            throw new IllegalStateException("Bad minor version: " + _minor);

        return _major * 10 + _minor;
    }
}
