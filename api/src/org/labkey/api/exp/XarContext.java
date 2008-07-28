/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.GUID;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: jeckels
 * Date: Dec 7, 2006
 */
public class XarContext
{
    private final Map<String, ExpData> _originalURLs;
    private final Map<String, ExpData> _originalCaseInsensitiveURLs;
    private final String _jobDescription;

    private final Map<String, String> _substitutions;

    public XarContext(XarContext parent)
    {
        _jobDescription = parent._jobDescription;
        _originalURLs = new HashMap<String, ExpData>(parent._originalURLs);
        _originalCaseInsensitiveURLs = new CaseInsensitiveHashMap<ExpData>(parent._originalURLs);
        _substitutions = new HashMap<String, String>(parent._substitutions);
    }

    public XarContext(String jobDescription, Container c, User user)
    {
        _jobDescription = jobDescription;
        _originalURLs = new HashMap<String, ExpData>();
        _originalCaseInsensitiveURLs = new CaseInsensitiveHashMap<ExpData>();
        _substitutions = new HashMap<String, String>();

        String path = c.getPath();
        if (path.startsWith("/"))
        {
            path = path.substring(1);
        }
        path = path.replace('/', '.');

        _substitutions.put("Container.path", path);
        _substitutions.put("Container.RowId", Integer.toString(c.getRowId()));

        _substitutions.put("XarFileId", "Xar-" + GUID.makeGUID());
        _substitutions.put("UserEmail", user.getEmail());
        _substitutions.put("UserName", user.getFullName());
        _substitutions.put("FolderLSIDBase", "urn:lsid:${LSIDAuthority}:${LSIDNamespace.Prefix}.Folder-${Container.RowId}");
        _substitutions.put("RunLSIDBase", "urn:lsid:${LSIDAuthority}:${LSIDNamespace.Prefix}.Run-${ExperimentRun.RowId}");

        _substitutions.put("LSIDAuthority", AppProps.getInstance().getDefaultLsidAuthority());
    }
    
    public String getJobDescription()
    {
        return _jobDescription;
    }

    public void addData(ExpData data, String originalURL)
    {
        originalURL = originalURL.replace('\\', '/');
        _originalURLs.put(originalURL, data);
        _originalCaseInsensitiveURLs.put(originalURL, data);
        
        if (originalURL.startsWith("file:/") && originalURL.length() > "file:/X:/".length())
        {
            int index = "file:/".length();
            if (Character.isLetter(originalURL.charAt(index++)) &&
                ':' == originalURL.charAt(index++) &&
                '/' == originalURL.charAt(index++))
            {
                String originalWithoutDriveLetter = "file:/" + originalURL.substring(index);
                _originalURLs.put(originalWithoutDriveLetter, data);
                _originalCaseInsensitiveURLs.put(originalWithoutDriveLetter, data);
            }
        }
    }

    private static final Pattern CYGDRIVE_PATTERN = Pattern.compile("/cygdrive/([a-z])/(.*)");

    public File findFile(String path, File relativeFile)
    {
        File f = findFile(path);
        if (f != null)
        {
            return f;
        }

        // If file can't be reached and it doesn't already have a drive letter, then attempt to append
        // the drive letter, if any, of the relativeFile
        if (null == NetworkDrive.getDrive(path))
        {
            String drivePrefix = NetworkDrive.getDrive(relativeFile.toString());
            String pathWithDrive = path;
            if (null != drivePrefix)
            {
                if (path.length() > 0 && path.charAt(0) != '\\' && path.charAt(0) != '/')
                {
                    pathWithDrive = drivePrefix + "/" + path;
                }
                else
                {
                    pathWithDrive = drivePrefix + path;
                }
            }

            f = new File(pathWithDrive);
            if (NetworkDrive.exists(f))
            {
                return f;
            }
        }

        f = new File(relativeFile, path);
        if (NetworkDrive.exists(f))
        {
            return f;
        }

        return null;
    }

    public File findFile(String path)
    {
        String lookupPath = path;
        if (lookupPath.indexOf(":/") < 0)
        {
            lookupPath = "file:/" + lookupPath;
        }

        // First, check if the XAR contains a file that was originally at that path
        lookupPath = lookupPath.replace('\\', '/');
        ExpData data = _originalURLs.get(lookupPath);
        if (data != null)
        {
            return data.getFile();
        }

        // Second, try looking for a case-insenstive match
        data = _originalCaseInsensitiveURLs.get(lookupPath);
        if (data != null)
        {
            return data.getFile();
        }

        // Next, check if the file exists on the file system at that exact location
        File f = new File(path);
        if (NetworkDrive.exists(f))
        {
            return f;
        }

        // Try resolving the Cygwin paths like /cygdrive/c/somepath/somefile.extension
        // to c:/somepath/somefile.extension
        Matcher matcher = CYGDRIVE_PATTERN.matcher(path);
        if (matcher.matches())
        {
            return findFile(matcher.group(1) + ":/" + matcher.group(2));
        }
        
        return null;
    }

    public Map<String, String> getSubstitutions()
    {
        return Collections.unmodifiableMap(_substitutions);
    }

    public void addSubstitution(String name, String value)
    {
        _substitutions.put(name, value);
    }
}
