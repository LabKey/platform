/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 */
public class URIUtil
{
    static public boolean isDescendant(URI base, URI descendant)
    {
        if (!descendant.getScheme().equalsIgnoreCase(base.getScheme()))
            return false;
        return descendant.getPath().startsWith(base.getPath());
    }

    static public URI resolve(URI root, URI base, String path)
    {
        URI ret;

        if (null == path)
            path = "";

        try
        {
            ret = base.resolve(path);
        }
        catch (IllegalArgumentException iae)
        {
            return null;
        }

        if (base.getSchemeSpecificPart().startsWith("////") && !ret.getSchemeSpecificPart().startsWith("////"))
        {
            // UNC paths have a null authority, which confuses URI.resolve and
            // causes it to lose a slash.
            try
            {
                ret = new URI(ret.getScheme(), "///" + ret.getSchemeSpecificPart(), ret.getFragment());
            }
            catch (URISyntaxException use)
            {
                return null;
            }
        }
        if (!isDescendant(root, ret))
            return null;
        return ret;
    }

    static public URI resolve(URI base, String path)
    {
        path = path == null ? null : path.replace("#", "%23");
        return resolve(base, base, path);
    }

    static public URI relativize(URI base, URI current)
    {
        if (!isDescendant(base, current))
            return null;
        return base.relativize(current);
    }

    static public URI getParentURI(URI base, URI current)
    {
        String path = trimTrailingSlash(current.getSchemeSpecificPart());
        int ichSlash = path.lastIndexOf('/');
        if (ichSlash <= 0)
        {
            path = "";
        }
        else
        {
            path = path.substring(0, ichSlash + 1);
        }
        try
        {
            URI ret = new URI(current.getScheme(), path, null);
            if (base != null && !isDescendant(base, ret))
                return null;
            return ret;
        }
        catch (URISyntaxException use)
        {
            return null;
        }
    }

    static public String trimTrailingSlash(String path)
    {
        if (path.endsWith("/"))
        {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    static public String getFilename(URI uri)
    {
        String path = trimTrailingSlash(uri.getPath());
        int ichSlash = path.lastIndexOf('/');
        if (ichSlash < 0)
            return path;
        return path.substring(ichSlash + 1);
    }

    static public boolean isDirectory(URI uri)
    {
        try
        {
            File file = new File(uri);
            return file.isDirectory();
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
    }

    static public URI[] listURIs(URI uri)
    {
        try
        {
            File file = new File(uri);
            File[] files = file.listFiles();
            if (files == null)
            {
                return new URI[0];
            }
            URI[] ret = new URI[files.length];
            for (int i = 0; i < files.length; i ++)
            {
                ret[i] = files[i].toURI();
            }
            return ret;
        }
        catch (IllegalArgumentException e)
        {
            return new URI[0];
        }
    }

    static public boolean exists(URI uri)
    {
        try
        {
            File file = new File(uri);
            return NetworkDrive.exists(file);
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
    }

    static public InputStream getInputStream(URI uri) throws Exception
    {
        if ("file".equals(uri.getScheme()))
            return new FileInputStream(new File(uri));
        return uri.toURL().openStream();
    }
}
