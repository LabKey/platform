/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.data;

import org.json.JSONArray;
import org.labkey.api.util.FileUtil;

import java.util.List;

public class TSVJSONWriter extends TSVWriter
{
    private final JSONArray _rows;
    private final String _filename;

    /**
     * Writes a JSONArray of JSONArrays to TSV.
     * @param filename The filename without a file extension
     * @param rows A JSONArray object that is expected to be an array of arrays. e.g. [[1,2,3], [4,5,6]].
     */
    public TSVJSONWriter(String filename, JSONArray rows)
    {
        _filename = filename;
        _rows = rows;
        _headerRowVisible = false;
    }

    private List<String> jsonArrayToStringList(JSONArray jsonArray)
    {
        return jsonArray.toList()
                .stream()
                .map(obj -> (obj == null) ? "" : String.valueOf(obj))
                .toList();
    }

    @Override
    protected int writeBody()
    {
        for (int i = 0; i < _rows.length(); i++)
        {
            List<String> values = jsonArrayToStringList(_rows.getJSONArray(i));
            writeLine(values);
        }

        return _rows.length();
    }

    @Override
    protected String getFilename()
    {
        return FileUtil.makeLegalName(_filename + "." + getFilenameExtension());
    }
}
