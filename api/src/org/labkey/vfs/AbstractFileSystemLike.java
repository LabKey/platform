/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.vfs;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.file.Path;

abstract public class AbstractFileSystemLike implements FileSystemLike
{
    final URI uri;
    final String scheme;
    final boolean caseSensitive;
    final String strUri;
    final boolean canDeleteRoot;
    final boolean canList = true;
    final boolean canRead;
    final boolean canWrite;

    protected AbstractFileSystemLike(URI uri, boolean caseSensitive, boolean canRead, boolean canWrite, boolean canDeleteRoot)
    {
        this.caseSensitive = caseSensitive;
        this.uri = uri;
        this.strUri = StringUtils.appendIfMissing(uri.toString(),"/");
        this.scheme = uri.getScheme();
        this.canRead = canRead;
        this.canWrite = canWrite;
        this.canDeleteRoot = canDeleteRoot;
    }

    /* wrapper to make sure we're being consistent */
    String toURIPath(org.labkey.api.util.Path path)
    {
        return path.encode();
    }

    @Override
    public URI getURI()
    {
        return uri;
    }

    @Override
    public URI getURI(FileLike fo)
    {
        return URI.create(strUri + fo.getPath().encode("",null));
    }

    @Override
    public String getScheme()
    {
        return scheme;
    }

    @Override
    public Path getNioPath(FileLike fo)
    {
        return Path.of(getURI(fo));
    }

    @Override
    public boolean canDeleteRoot()
    {
        return canDeleteRoot;
    }

    @Override
    public boolean canList()
    {
        return canList;
    }

    @Override
    public boolean canReadFiles()
    {
        return canRead;
    }

    @Override
    public boolean canWriteFiles()
    {
        return canWrite;
    }

    @Override
    public org.labkey.api.util.Path parsePath(String str)
    {
        var ret = caseSensitive ?
                org.labkey.api.util.Path.parseCaseSensitive(str) :
                org.labkey.api.util.Path.parse(str);
        assert caseSensitive == ret.isCaseSensitive();
        return ret;
    }

    @Override
    public org.labkey.api.util.Path pathOf(org.labkey.api.util.Path path)
    {
        if (path.isCaseSensitive() == caseSensitive)    // don't really need this, but it makes it easier to set a breakpoint when !=
            return path;
        var ret = caseSensitive ?
                path.caseSensitive() :
                path.caseInsensitive();
        assert ret.isCaseSensitive() == caseSensitive;
        return ret;
    }
}
