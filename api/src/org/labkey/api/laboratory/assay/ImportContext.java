/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import org.apache.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.view.ViewContext;

import java.io.File;

/**
 * User: bimber
 * Date: 1/9/13
 * Time: 5:12 PM
 */
public class ImportContext
{
    private JSONObject _json;
    private File _file;
    private String _fileName;
    private ViewContext _ctx;
    private ParserErrors _errors;

    public ImportContext(JSONObject json, File file, String fileName, ViewContext ctx)
    {
        _json = json;
        _file = file;
        _fileName = fileName;
        _ctx = ctx;

        Level level = Level.ALL;
        if (json != null && json.containsKey("errorLevel"))
        {
            level = Level.toLevel((String)json.get("errorLevel"));
        }
        _errors = new ParserErrors(level);
    }

    public JSONObject getJson()
    {
        return _json;
    }

    public File getFile()
    {
        return _file;
    }

    public String getFileName()
    {
        return _fileName;
    }

    public ViewContext getViewContext()
    {
        return _ctx;
    }

    public JSONArray getResultRowsFromJson()
    {
        if (_json.has("ResultRows"))
        {
            return _json.getJSONArray("ResultRows");
        }

        return null;
    }

    public JSONObject getPromotedResultsFromJson()
    {
        if (_json.has("Results"))
        {
            return _json.getJSONObject("Results");
        }

        return null;
    }

    public Integer getTemplateIdFromJson()
    {
        return _json.getInt("TemplateId");
    }

    public ParserErrors getErrors()
    {
        return _errors;
    }

    public JSONObject getRunProperties()
    {
        return _json.getJSONObject("Run");
    }
}
