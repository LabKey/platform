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
package org.labkey.pipeline.xstream;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.file.PathMapper;

import java.util.Map;

/**
 * PathMapper class
 * <p/>
 * Created: Oct 4, 2007
 *
 * @author bmaclean
 */
public class PathMapperImpl implements PathMapper
{
    private static Logger _log = Logger.getLogger(PathMapperImpl.class);

    /**
     * Prefix mappings:
     * <ul>
     * <li>file:/C:/root -> file:/home/root
     * <li>file:/C:/projects/root1 -> file:/home/user/projects/root1
     * <li>file:/C:/projects/root2 -> file:/home/user/projects/root2
     * <ul>
     */
    private Map<String, String> _pathMap;
    private boolean _remoteIgnoreCase;
    private boolean _localIgnoreCase;

    public PathMapperImpl()
    {
    }

    public Map<String, String> getPathMap()
    {
        return _pathMap;
    }

    public void setPathMap(Map<String, String> pathMap)
    {
        _pathMap = pathMap;
    }

    public boolean isRemoteIgnoreCase()
    {
        return _remoteIgnoreCase;
    }

    public void setRemoteIgnoreCase(boolean remoteIgnoreCase)
    {
        _remoteIgnoreCase = remoteIgnoreCase;
    }

    public boolean isLocalIgnoreCase()
    {
        return _localIgnoreCase;
    }

    public void setLocalIgnoreCase(boolean localIgnoreCase)
    {
        _localIgnoreCase = localIgnoreCase;
    }

    /**
     * If there are any prefix matches, map from remote system path
     * to local path.
     *
     * @param path remote path
     * @return local path
     */
    public String remoteToLocal(String path)
    {
        if (_pathMap != null && path != null)
        {
            for (Map.Entry<String, String> e : _pathMap.entrySet())
            {
                String prefix = e.getKey();
                if (match(prefix, path, _remoteIgnoreCase))
                    return e.getValue() + path.substring(prefix.length());
            }
        }

        return path;
    }

    /**
     * If there are any prefix matches, map from local path
     * to remote system path.
     *
     * @param path remote path
     * @return local path
     */
    public String localToRemote(String path)
    {
        if (_pathMap != null && path != null)
        {
            for (Map.Entry<String, String> e : _pathMap.entrySet())
            {
                String prefix = e.getValue();
                if (match(prefix, path, _localIgnoreCase))
                    return e.getKey() + path.substring(prefix.length());
            }
        }

        return path;
    }

    /**
     * Check if a path starts with a given path prefix.  If the prefix
     * itself does not end with '/', then the next character in the path
     * must be a '/'.  It is assumed that the strings are URIs like
     * 'file:/C:/root'.
     *
     * @param prefix a URI prefix like 'file:/C:/root'
     * @param path a URL path like 'file:/C:/root/subdir1/subdir2/file.txt'
     * @param ignoreCase True if match should be done case insensitive
     * @return true if the path starts with the prefix.
     */
    private boolean match(String prefix, String path, boolean ignoreCase)
    {
        final int lenPath = path.length();
        final int lenPrefix = prefix.length();
        if (lenPath < lenPrefix)
            return false;

        String start = path.substring(0, prefix.length());

        boolean ret;
        if (ignoreCase)
            ret = start.equalsIgnoreCase(prefix);
        else
            ret = start.equals(prefix);

        return ret &&
            (lenPath == lenPrefix ||
             prefix.charAt(lenPrefix - 1) == '/' ||
             path.charAt(lenPrefix) == '/');
    }
}
