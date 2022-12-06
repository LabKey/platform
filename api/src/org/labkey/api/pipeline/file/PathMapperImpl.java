/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Translates paths from one machine to another, assuming they have access to a shared network file system.
 * Created: Oct 4, 2007
 * @author bmaclean
 */
public class PathMapperImpl implements PathMapper
{
    /**
     * Prefix mappings (remote to local):
     * <ul>
     * <li>file:/C:/root -> file:/home/root
     * <li>file:/C:/projects/root1 -> file:/home/user/projects/root1
     * <li>file:/C:/projects/root2 -> file:/home/user/projects/root2
     * <ul>
     */
    private Map<URI, URI> _uriMap = new LinkedHashMap<>();
    private boolean _remoteIgnoreCase;
    private boolean _localIgnoreCase;
    private ValidationException _validationErrors;

    public PathMapperImpl()
    {
    }

    public PathMapperImpl(ValidationException ve)
    {
        _validationErrors = ve;
    }

    public PathMapperImpl(Map<String, String> pathMap)
    {
        setPathMap(pathMap);
    }

    public PathMapperImpl(Map<String, String> pathMap, boolean remoteIgnoreCase, boolean localIgnoreCase)
    {
        this(pathMap);
        _remoteIgnoreCase = remoteIgnoreCase;
        _localIgnoreCase = localIgnoreCase;
    }

    /**
     * Returns a copy of the path map as strings
     * @return
     */
    @Override
    @Deprecated //Use getURIPathMap instead
    public Map<String, String> getPathMap()
    {
        Map<String, String> stringMap = new LinkedHashMap<>();
        _uriMap.forEach((k, v) -> stringMap.put(k.toString(), v.toString()));
        return stringMap;
    }

    /**
     *
     * @return unmodifiable copy of underlying uri map
     */
    @Override
    public Map<URI, URI> getURIPathMap()
    {
        return Collections.unmodifiableMap(_uriMap);
    }

    public void setURIPathMap(Map<URI, URI> pathMap)
    {
        _uriMap = pathMap;
    }

    @Deprecated // Use setURIPathMap
    public void setPathMap(Map<String, String> pathMap)
    {
        _uriMap = new LinkedHashMap<>();
        // TODO: Change may have side-effects old version would have kept pre-existing keys not in pathMap arg...
        pathMap.forEach((remote, local) -> {
            //URI relativize will sub final entry w/o the closing '/' //TODO do we map files to files directly?...
            String rURI = remote.endsWith("/") ? remote : remote + '/' ;
            String lURI = local.endsWith("/") ? local : local + '/' ;
            _uriMap.put(URI.create(rURI).normalize(), URI.create(lURI).normalize());
        });
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

    @Override
    public ValidationException getValidationErrors()
    {
        return _validationErrors;
    }

    /**
     * If there are any prefix matches, map from remote system path
     * to local path.
     *
     * @param path remote path
     * @return local path
     */
    @Override
    public String remoteToLocal(String path)
    {
        //Use FileUtil to conform file:/ to file:///
        return capitalizeDriveLetter(FileUtil.uriToString(remoteToLocal(FileUtil.createUri(path))));
    }

    private String capitalizeDriveLetter(String path)
    {
        if (path == null)
        {
            return null;
        }

        String startsWith = path.startsWith("file:///") ? "file:///" :
                            path.startsWith("file:/") ? "file:/" : "";
        int driveLetterIndex = startsWith.length();
        int colonLetterIndex = driveLetterIndex + 1;
        
        if (!startsWith.isEmpty() && path.length() > colonLetterIndex && path.charAt(colonLetterIndex) == ':' && Character.isLowerCase(path.charAt(driveLetterIndex)))
        {
            path = path.substring(0, startsWith.length()) + Character.toUpperCase(path.charAt(driveLetterIndex)) + path.substring(colonLetterIndex);
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
    @Override
    public String localToRemote(String path)
    {
        //Use Util to conform file:/ to file:///
        return FileUtil.uriToString(localToRemote(FileUtil.createUri(path)));
    }

    @Override
    public URI localToRemote(URI path)
    {
        return mapPath(path, _localIgnoreCase, Map.Entry::getValue, Map.Entry::getKey);
    }

    @Override
    public URI remoteToLocal(URI path)
    {
        return mapPath(path, _remoteIgnoreCase, Map.Entry::getKey, Map.Entry::getValue);
    }

    private URI mapPath(URI mapMe, boolean ignoreCase, Function<Map.Entry<URI, URI>, URI> getStart, Function<Map.Entry<URI, URI>, URI> getFinish)
    {
        if (_uriMap != null && mapMe != null)
        {
            Map.Entry<URI, URI> to = null;
            URI bestRel = null;
            for(Map.Entry<URI, URI> e : _uriMap.entrySet())
            {
                URI from = getStart.apply(e);
                String prefix = StringUtils.substring(mapMe.getPath(), from.getPath().length());  //Path prefix if ignoring case
                URI rel = ignoreCase ? FileUtil.createUri(prefix) : from.relativize(mapMe);

                // keeping the "match" method to maintain case sensitivity behavior
                if (match(from.getPath(), mapMe.getPath(), ignoreCase) && (bestRel == null || bestRel.getPath().length() > rel.getPath().length()))
                {
                    to = e;
                    bestRel = rel;
                }
            }

            if (bestRel != null)
                return getFinish.apply(to).resolve(bestRel);
        }

        return mapMe;
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

        boolean ret = ignoreCase ? StringUtils.startsWithIgnoreCase(path, prefix) : path.startsWith(prefix);

        // Accept either a slash or a colon from a Windows drive letter
        return ret &&
            (lenPath == lenPrefix ||
             prefix.charAt(lenPrefix - 1) == '/' || prefix.charAt(lenPrefix - 1) == ':' ||
             path.charAt(lenPrefix) == '/' || path.charAt(lenPrefix) == ':');
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("localIgnoreCase", _localIgnoreCase);
        json.put("remoteIgnoreCase", _remoteIgnoreCase);

        JSONArray jsonPaths = new JSONArray();
        json.put("paths", jsonPaths);
        for (Map.Entry<URI, URI> pair : _uriMap.entrySet())
        {
            JSONObject jsonPair = new JSONObject();
            jsonPair.put("remoteURI", pair.getKey());
            jsonPair.put("localURI", pair.getValue());
            jsonPaths.put(jsonPair);
        }

        return json;
    }

    public static PathMapperImpl fromJSON(JSONObject json)
    {
        return fromJSON(json, false);
    }

    // Do not throw if we are binding properties of a form so that we can return
    // validation errors to the client
    private static void handleError(String prop, String message, ValidationException errors)
    {
        if (null == errors)
            throw new RuntimeException(message);

        errors.addError(new PropertyValidationError(message, prop));
    }

    public static PathMapperImpl fromJSON(JSONObject json, boolean trackValidationErrors)
    {
        JSONArray jsonPaths = (JSONArray )json.get("paths");
        ValidationException errors = trackValidationErrors ? new ValidationException() : null;

        Map<String, String> map = new LinkedHashMap<>();
        for (Object entry : jsonPaths)
        {
            if (!(entry instanceof JSONObject mapEntry))
                continue;
            String localURI = StringUtils.trimToNull(mapEntry.getString("localURI"));
            String remoteURI = StringUtils.trimToNull(mapEntry.getString("remoteURI"));

            //Ignore blank rows, could throw an error here -- but lets be flexible
            if (localURI == null && remoteURI == null)
                continue;

            // CONSIDER: CustomApiForm.bindProperties() should accept an Errors object or allow throwing BindException
            if (localURI == null)
            {
                handleError("localURI", "Local URI must not be empty", errors);
                return new PathMapperImpl(errors);
            }

            if (remoteURI == null)
            {
                handleError("remoteURI", "Remote URI must not be empty", errors);
                return new PathMapperImpl(errors);
            }

            // Convert path to URI and validate paths are absolute
            // for local files, resolve. -  which strips .. and . from the path so that
            // we can compare apples to apples (c:/bar/./foo versus c:/bar/foo)
            localURI = toURI(localURI, true, errors);
            if (localURI == null)
                return new PathMapperImpl(errors);
            remoteURI = toURI(remoteURI, false, errors);
            if (remoteURI == null)
                return new PathMapperImpl(errors);

            if (!localURI.endsWith("/"))
                localURI = localURI.concat("/");

            if (!remoteURI.endsWith("/"))
                remoteURI = remoteURI.concat("/");

            map.put(remoteURI, localURI);
        }

        if (map.size() == 0)
        {
            handleError("Paths", "No file shares enabled", errors);
            return new PathMapperImpl(errors);
        }

        boolean localIgnoreCase = json.optBoolean("localIgnoreCase", false);
        boolean remoteIgnoreCase = json.optBoolean("remoteIgnoreCase", false);

        return new PathMapperImpl(map, remoteIgnoreCase, localIgnoreCase);
    }

    private static String toURI(String path)
    {
        return toURI(path, false, null);
    }
    private static String toURI(String path, boolean resolve)
    {
        return toURI(path, resolve, null);
    }

    private static String toURI(String path, boolean resolve, ValidationException errors)
    {
        if (!path.startsWith("file:"))
        {
            File f = new File(path);
            if (!f.isAbsolute())
            {
                handleError(path, "File URI '" + path + "' must be an absolute path", errors);
                return null;
            }

            if (resolve)
                f = FileUtil.getAbsoluteCaseSensitiveFile(f);

            return FileUtil.uriToString(f.toURI());
        }

        URI uri;
        try
        {
            uri = new URI(path);
            if (!uri.isAbsolute())
            {
                handleError(path, "File URI '" + path + "' must be an absolute path", errors);
                return null;

            }

            if (resolve)
                uri = FileUtil.getAbsoluteCaseSensitiveFile(new File(uri)).toURI();

            return FileUtil.uriToString(uri);
        }
        catch (URISyntaxException | IllegalArgumentException e)
        {
            handleError(path, "File URI '" + path + "' syntax error: " + e.getMessage(), errors);
            return null;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testCaseSensitiveMapping()
        {
            Map<String, String> m = new HashMap<>();
            m.put("file:/T:/edi", "file:/home/edi");
            m.put("file:///T:/edi", "file:///home/edi");
            m.put("file:/T:/data", "file:/data");
            m.put("file:///T:/data", "file:///data");

            PathMapper mapper = new PathMapperImpl(m, false, false);

            assertEquals("file:///T:/edi/testFile.txt", mapper.localToRemote("file:/home/edi/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:/data/testFile.txt"));
            assertEquals("file:///Data/testFile.txt", mapper.localToRemote("file:/Data/testFile.txt"));

            assertEquals("file:///home/edi/testFile.txt", mapper.remoteToLocal("file:/T:/edi/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:/T:/data/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.remoteToLocal("file:/t:/data/testFile.txt"));
            assertEquals("file:///E:/data/testFile.txt", mapper.remoteToLocal("file:/e:/data/testFile.txt"));

            assertEquals("file:///T:/edi/testFile.txt", mapper.localToRemote("file:///home/edi/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:///data/testFile.txt"));
            assertEquals("file:///Data/testFile.txt", mapper.localToRemote("file:///Data/testFile.txt"));

            assertEquals("file:///home/edi/testFile.txt", mapper.remoteToLocal("file:///T:/edi/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:///T:/data/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.remoteToLocal("file:///t:/data/testFile.txt"));
            assertEquals("file:///E:/data/testFile.txt", mapper.remoteToLocal("file:///e:/data/testFile.txt"));
        }

        @Test
        public void testCaseInsensitiveMapping()
        {
            Map<String, String> m = new HashMap<>();
            m.put("file:/T:/edi", "file:/home/edi");
            m.put("file:/T:/data", "file:/data");
            m.put("file:///T:/edi", "file:///home/edi");
            m.put("file:///T:/data", "file:///data");
            PathMapper mapper = new PathMapperImpl(m, true, true);

            assertEquals("file:///T:/edi/testFile.txt", mapper.localToRemote("file:/home/edi/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:/data/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:/Data/testFile.txt"));

            assertEquals("file:///home/edi/testFile.txt", mapper.remoteToLocal("file:/T:/edi/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:/T:/data/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:/t:/data/testFile.txt"));

            assertEquals("file:///T:/edi/testFile.txt", mapper.localToRemote("file:///home/edi/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:///data/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:///Data/testFile.txt"));

            assertEquals("file:///home/edi/testFile.txt", mapper.remoteToLocal("file:///T:/edi/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:///T:/data/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:///t:/data/testFile.txt"));
        }

        @Test
        public void testLongestPrefixMapping()
        {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("file:/T:", "file:/home/stedi");
            m.put("file:/T:/data", "file:/data");
            m.put("file:/T:/edi", "file:/home/edi");
            m.put("file:///T:", "file:///home/stedi");
            m.put("file:///T:/data", "file:///data");
            m.put("file:///T:/edi", "file:///home/edi");
            PathMapper mapper = new PathMapperImpl(m, false, false);

            assertEquals("file:///T:/edi/testFile.txt", mapper.localToRemote("file:/home/edi/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:/data/testFile.txt"));
            assertEquals("file:///T:/testFile.txt", mapper.localToRemote("file:/home/stedi/testFile.txt"));

            assertEquals("file:///home/stedi/testFile.txt", mapper.remoteToLocal("file:/T:/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:/T:/data/testFile.txt"));
            assertEquals("file:///home/edi/testFile.txt", mapper.remoteToLocal("file:/T:/edi/testFile.txt"));

            assertEquals("file:///T:/edi/testFile.txt", mapper.localToRemote("file:///home/edi/testFile.txt"));
            assertEquals("file:///T:/data/testFile.txt", mapper.localToRemote("file:///data/testFile.txt"));
            assertEquals("file:///T:/testFile.txt", mapper.localToRemote("file:///home/stedi/testFile.txt"));

            assertEquals("file:///home/stedi/testFile.txt", mapper.remoteToLocal("file:///T:/testFile.txt"));
            assertEquals("file:///data/testFile.txt", mapper.remoteToLocal("file:///T:/data/testFile.txt"));
            assertEquals("file:///home/edi/testFile.txt", mapper.remoteToLocal("file:///T:/edi/testFile.txt"));
        }

        @Test
        public void testCapitalizeDriveLetter()
        {
            PathMapperImpl impl = new PathMapperImpl();
            assertEquals("file:/T:/test", impl.capitalizeDriveLetter("file:/t:/test"));
            assertEquals("file:/T:/test", impl.capitalizeDriveLetter("file:/T:/test"));
            assertEquals("file:/test", impl.capitalizeDriveLetter("file:/test"));
            assertEquals("", impl.capitalizeDriveLetter(""));
            assertEquals("file:/", impl.capitalizeDriveLetter("file:/"));
            assertEquals("f", impl.capitalizeDriveLetter("f"));

            assertEquals("file:///T:/test", impl.capitalizeDriveLetter("file:///t:/test"));
            assertEquals("file:///T:/test", impl.capitalizeDriveLetter("file:///T:/test"));
            assertEquals("file:///test", impl.capitalizeDriveLetter("file:///test"));
            assertEquals("file:///", impl.capitalizeDriveLetter("file:///"));
        }

        @Test
        public void testFileRoot()
        {
            Map<String, String> m = new HashMap<>();
            m.put("file:/T:", "file:/");
            m.put("file:///T:", "file:///");

            PathMapper mapper = new PathMapperImpl(m, false, false);

            assertEquals("file:///T:/testFile.txt", mapper.localToRemote("file:/testFile.txt"));
            assertEquals("file:///T:/testFile.txt", mapper.localToRemote("file:///testFile.txt"));
            assertEquals("file:///testFile.txt", mapper.remoteToLocal("file:///T:/testFile.txt"));
            assertEquals("file:///testFile.txt", mapper.remoteToLocal("file:/T:/testFile.txt"));
        }

        @Test
        public void testFileRoot_URI()
        {
            Map<String, String> m = new HashMap<>();
            m.put("file:/T:", "file:/");
            m.put("file:///T:", "file:///");
            m.put("file://somehost/path/to/file", "file://localhost:8080/T:/local/file/path/");

            PathMapper mapper = new PathMapperImpl(m, false, false);

            URI driveLetter = URI.create("file:/T:/testFile.txt");
            URI fileRoot = URI.create("file:/testFile.txt");
            URI tripleDriveLetter = URI.create("file:///T:/testFile.txt");
            URI tripleFileRoot = URI.create("file:///testFile.txt");
            URI somehostFile = URI.create("file://somehost/path/to/file" + "/myFile");

            assertEquals(0, driveLetter.compareTo(mapper.localToRemote(URI.create("file:/testFile.txt"))));
            assertEquals(0, tripleDriveLetter.compareTo(mapper.localToRemote(URI.create("file:///testFile.txt"))));
            assertEquals(0, fileRoot.compareTo(mapper.remoteToLocal(URI.create("file:///T:/testFile.txt"))));
            assertEquals(0, tripleFileRoot.compareTo(mapper.remoteToLocal(URI.create("file:/T:/testFile.txt"))));
            assertEquals(0, somehostFile.compareTo(mapper.localToRemote(URI.create("file://localhost:8080/T:/local/file/path/myFile"))));
        }
    }
}
