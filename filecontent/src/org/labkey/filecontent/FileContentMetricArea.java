/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.filecontent;

import org.labkey.api.usageMetrics.SimpleMetricArea;

public enum FileContentMetricArea implements SimpleMetricArea
{
    NAMED_FILE_SETS("namedFileSets", "Uses of named file sets: legacySendFile for downloads via the sendFile action, webdavResolve for user WebDAV lookups of a file set (search crawler excluded)");

    private final Definition _definition;

    FileContentMetricArea(String areaName, String description)
    {
        _definition = new Definition(FileContentModule.NAME, areaName, description);
    }

    @Override
    public Definition getDefinition()
    {
        return _definition;
    }
}
