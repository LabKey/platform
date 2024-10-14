/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import static org.labkey.api.util.FileUtil.FILE_SCHEME;

/**
 */
public class URIUtil
{
    static public boolean isDescendant(URI base, URI descendant)
    {
        var baseSchema = StringUtils.defaultString(base.getScheme(), FILE_SCHEME);
        var descendantSchema = StringUtils.defaultString(descendant.getScheme(), FILE_SCHEME);

        if (!descendantSchema.equalsIgnoreCase(baseSchema))
            return false;

        // append '/' to both paths to handle these cases
        //     "/atoz" starts with "/a" but is not a descendant
        //     "/a" does not start with "/a/" but should be considered equal
        var basePath = StringUtils.appendIfMissing(base.getPath(),"/");
        var descendantPath = StringUtils.appendIfMissing(descendant.getPath(),"/");

        if (descendantPath.contains("/../"))
            return false;

        return descendantPath.startsWith(basePath);
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
        if (FILE_SCHEME.equals(uri.getScheme()))
            return new FileInputStream(new File(uri));
        return uri.toURL().openStream();
    }

    /**
     * Normalizes URI and converges file:/ to file:///
     * @param uri to normalize
     * @return a new normalized uri
     */
    @Nullable
    public static URI normalizeUri(URI uri) throws URISyntaxException
    {
        if (uri == null)
            return null;

        URI res = uri;
        if (FILE_SCHEME.equalsIgnoreCase(uri.getScheme())
                && !StringUtils.startsWith(uri.getRawSchemeSpecificPart(), "///")
                && StringUtils.isBlank(uri.getHost()))
        {
            //Conform file:/ to file:///
            res = new URIBuilder(uri).setHost("").build();
        }
        return res.normalize();
    }

    /**
     * Return true if the string contains characters typically found in URIs but doesn't attempt to parse.
     */
    public static boolean hasURICharacters(String propertyURI)
    {
        return StringUtils.containsAny(propertyURI, ':', '/', '#', '%', '?') && propertyURI.startsWith("urn:");
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testTrimTrailingSlash()
        {
            assertEquals("foo", trimTrailingSlash("foo"));
            assertEquals("foo", trimTrailingSlash("foo/"));
            assertEquals("foo/bar", trimTrailingSlash("foo/bar"));
            assertEquals("foo/bar", trimTrailingSlash("foo/bar/"));
        }

        @Test
        public void testHasURICharacters()
        {
            assertTrue(hasURICharacters("urn:foo"));
            assertFalse(hasURICharacters("foo"));
            assertTrue(hasURICharacters("urn:foo:bar"));
            assertFalse(hasURICharacters("foo:bar"));
            assertTrue(hasURICharacters("urn:foo/bar"));
            assertFalse(hasURICharacters("foo/bar"));
            assertTrue(hasURICharacters("urn:foo/bar#baz"));
            assertFalse(hasURICharacters("foo/bar#baz"));
            assertTrue(hasURICharacters("urn:foo/bar#baz?qux"));
            assertFalse(hasURICharacters("foo/bar#baz?qux"));
            assertTrue(hasURICharacters("urn:foo/bar#baz?qux%quux"));
            assertFalse(hasURICharacters("foo/bar#baz?qux%quux"));
        }

        @Test
        public void testGetFilename()
        {
            assertEquals("myfile.txt", getFilename(URI.create("file:///data/myfile.txt")));
            assertEquals("myfile.txt", getFilename(URI.create("file:///data/myfile.txt/")));
            assertEquals("myfile.txt", getFilename(URI.create("data/myfile.txt")));
            assertEquals("myfile.txt", getFilename(URI.create("myfile.txt")));
        }

        @Test
        public void testIsDescendant()
        {
            for (var basePrefix : Arrays.asList("","file:","file://"))
            {
                for (var baseTrailing : Arrays.asList("", "/"))
                {
                    for (var descPrefix : Arrays.asList("", "file:", "file://"))
                    {
                        URI base = URI.create(basePrefix + "/a/b%27b/c" + trimTrailingSlash(baseTrailing));
                        assertTrue(base.toString(),  isDescendant(base, URI.create(basePrefix + "/a/b'b/c/myfile.txt")));
                        assertTrue(base.toString(),  isDescendant(base, URI.create(basePrefix + "/a/b%27b/c/myfile.txt")));
                        assertTrue(base.toString(),  isDescendant(base, URI.create(basePrefix + "/a/b'b/c/d/myfile.txt")));
                        assertFalse(base.toString(), isDescendant(base, URI.create(basePrefix + "/a/b'b/c/../c/myfile.txt")));
                        assertFalse(base.toString(), isDescendant(base, URI.create(basePrefix + "/a/b%27b/c/../myfile.txt")));
                        assertFalse(base.toString(), isDescendant(base, URI.create(basePrefix + "/a/b'b/myfile.txt")));
                        assertFalse(base.toString(), isDescendant(base, URI.create(basePrefix + "/a/b'b/cxyz/d")));
                        assertFalse(base.toString(), isDescendant(base, URI.create(basePrefix + "/a/b%27b/c.txt")));
                        // test equal paths
                        assertTrue(base.toString(),  isDescendant(base, URI.create(basePrefix + "/a/b'b/c")));
                        assertTrue(base.toString(),  isDescendant(base, URI.create(basePrefix + "/a/b'b/c/")));
                    }
                }
            }
        }
    }
}
