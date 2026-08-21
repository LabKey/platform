/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.api.webdav;

import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

public class DavPath extends Path
{
    private static final boolean isCaseSensitive = !FileUtil.isCaseInsensitiveFileSystem();

    public DavPath(Path path)
    {
        super(path, isCaseSensitive);
    }

    public DavPath(Path path, boolean caseSensitive)
    {
        super(path, caseSensitive);
    }
}
