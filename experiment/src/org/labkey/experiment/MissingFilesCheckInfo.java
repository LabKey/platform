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
package org.labkey.experiment;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class MissingFilesCheckInfo
{
    long _missingFilesCount = 0;
    long _validFilesCount = 0;
    Set<String> _missingFilePaths = new HashSet<>();

    public MissingFilesCheckInfo()
    {}

    public long getMissingFilesCount()
    {
        return _missingFilesCount;
    }

    public long getValidFilesCount()
    {
        return _validFilesCount;
    }

    public Set<String> getMissingFilePaths()
    {
        return _missingFilePaths;
    }

    public void addMissingFile(String path, boolean trackMissingFiles)
    {
        _missingFilesCount++;
        if (trackMissingFiles)
            _missingFilePaths.add(path);
    }

    public void incrementValidFilesCount()
    {
        _validFilesCount++;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("missingFilesCount", _missingFilesCount);
        json.put("validFilesCount", _validFilesCount);
        if (!_missingFilePaths.isEmpty())
            json.put("missingFilePaths", _missingFilePaths);
        return json;
    }
}
