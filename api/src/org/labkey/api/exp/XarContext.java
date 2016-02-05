/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
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
    private final Container _container;
    private final User _user;
    private PipelineJob _job;

    private final Map<String, String> _substitutions;

    private static final String XAR_FILE_ID_NAME = "XarFileId";
    private static final String EXPERIMENT_RUN_ID_NAME = "ExperimentRun.RowId";
    private static final String CONTAINER_ID_NAME = "Container.RowId";
    private static final String FOLDER_LSID_BASE_NAME = "FolderLSIDBase";
    private static final String RUN_LSID_BASE_NAME = "RunLSIDBase";
    private static final String LSID_AUTHORITY_NAME = "LSIDAuthority";

    public static final String XAR_FILE_ID_SUBSTITUTION = createSubstitution(XAR_FILE_ID_NAME);
    public static final String EXPERIMENT_RUN_ID_SUBSTITUTION = createSubstitution(EXPERIMENT_RUN_ID_NAME);
    public static final String CONTAINER_ID_SUBSTITUTION = createSubstitution(CONTAINER_ID_NAME);
    public static final String FOLDER_LSID_BASE_SUBSTITUTION = createSubstitution(FOLDER_LSID_BASE_NAME);
    public static final String RUN_LSID_BASE_SUBSTITUTION = createSubstitution(RUN_LSID_BASE_NAME);
    public static final String LSID_AUTHORITY_SUBSTITUTION = createSubstitution(LSID_AUTHORITY_NAME);
    private static final String EXPERIMENT_RUN_LSID_NAME = "ExperimentRun.LSID";
    private static final String EXPERIMENT_RUN_NAME_NAME = "ExperimentRun.Name";

    public XarContext(XarContext parent)
    {
        _jobDescription = parent._jobDescription;
        _originalURLs = new HashMap<>(parent._originalURLs);
        _originalCaseInsensitiveURLs = new CaseInsensitiveHashMap<>(parent._originalURLs);
        _substitutions = new HashMap<>(parent._substitutions);
        _container = parent.getContainer();
        _user = parent.getUser();
    }

    public XarContext(PipelineJob job)
    {
        this(job.getDescription(), job.getContainer(), job.getUser());
        _job = job;
    }

    public XarContext(String jobDescription, Container c, User user)
    {
        this(jobDescription, c, user, AppProps.getInstance().getDefaultLsidAuthority());
    }

    public XarContext(String jobDescription, Container c, User user, String defaultLsidAuthority)
    {
        _jobDescription = jobDescription;
        _originalURLs = new HashMap<>();
        _originalCaseInsensitiveURLs = new CaseInsensitiveHashMap<>();
        _substitutions = new HashMap<>();

        String path = c.getPath();
        if (path.startsWith("/"))
        {
            path = path.substring(1);
        }
        path = path.replace('/', '.');

        _substitutions.put("Container.path", path);
        _substitutions.put(CONTAINER_ID_NAME, Integer.toString(c.getRowId()));

        _substitutions.put(XAR_FILE_ID_NAME, "Xar-" + GUID.makeGUID());
        if (user != null)
        {
            _substitutions.put("UserEmail", user.getEmail());
            _substitutions.put("UserName", user.getFullName());
        }
        _substitutions.put(FOLDER_LSID_BASE_NAME, "urn:lsid:" + LSID_AUTHORITY_SUBSTITUTION + ":${LSIDNamespace.Prefix}.Folder-" + CONTAINER_ID_SUBSTITUTION);
        _substitutions.put(RUN_LSID_BASE_NAME, "urn:lsid:" + LSID_AUTHORITY_SUBSTITUTION + ":${LSIDNamespace.Prefix}.Run-" + EXPERIMENT_RUN_ID_SUBSTITUTION);

        _substitutions.put(LSID_AUTHORITY_NAME, defaultLsidAuthority);

        _container = c;
        _user = user;
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

        // Check if it's in the current directory, stripping off any extra path from the file name
        int index = path.lastIndexOf("/");
        if (index != -1)
        {
            String filename = path.substring(index + 1);
            if (!filename.isEmpty())
            {
                f = new File(relativeFile, filename);
                if (NetworkDrive.exists(f))
                {
                    return f;
                }
            }
        }

        // Do the same for Windows paths
        index = path.lastIndexOf("\\");
        if (index != -1)
        {
            String filename = path.substring(index + 1);
            if (!filename.isEmpty())
            {
                f = new File(relativeFile, filename);
                if (NetworkDrive.exists(f))
                {
                    return f;
                }
            }
        }

        // Finally, try using the pipeline's path mapper if we have one to
        // translate from a cluster path to a webserver path
        // Path mappers deal with URIs, not file paths
        String uri = "file:" + (path.startsWith("/") ? path : "/" + path);
        // This PathMapper considers "local" from a cluster node's point of view.
        for (RemoteExecutionEngine<?> engine : PipelineJobService.get().getRemoteExecutionEngines())
        {
            String mappedURI = engine.getConfig().getPathMapper().localToRemote(uri);
            // If we have translated Windows paths, they won't be legal URIs, so convert slashes
            mappedURI = mappedURI.replace('\\', '/');
            try
            {
                f = new File(new URI(mappedURI));
                if (NetworkDrive.exists(f))
                {
                    return f;
                }
            }
            catch (URISyntaxException e)
            {
            }
        }

        return null;
    }

    public File findFile(String path)
    {
        String lookupPath = path;
        if (!lookupPath.contains(":/"))
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

    public void setCurrentRun(ExpRun run)
    {
        addSubstitution(EXPERIMENT_RUN_ID_NAME, Integer.toString(run.getRowId()));
        addSubstitution(EXPERIMENT_RUN_LSID_NAME, run.getLSID());
        addSubstitution(EXPERIMENT_RUN_NAME_NAME, run.getName());
    }

    public static String createSubstitution(String name)
    {
        return "${" + name + "}";
    }

    public Container getContainer()
    {
        return _container;
    }

    public User getUser()
    {
        return _user;
    }

    public @Nullable PipelineJob getJob()
    {
        return _job;
    }
}
