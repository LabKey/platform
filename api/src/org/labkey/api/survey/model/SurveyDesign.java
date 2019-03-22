/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.survey.model;

import org.json.JSONObject;
import org.labkey.api.data.Entity;
import org.labkey.api.security.User;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: klum
 * Date: 12/11/12
 */
public class SurveyDesign extends Entity
{
    private int _rowId;
    private String _label;
    private String _description;
    private String _queryName;
    private String _schemaName;
    private String _metadata;

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getMetadata()
    {
        return _metadata;
    }

    public void setMetadata(String metadata)
    {
        _metadata = metadata;
    }

    public JSONObject toJSON(User currentUser, boolean stringifyMetadata)
    {
        JSONObject o = new JSONObject(this);

        // gets around the problem where the metadata is not properly stringified
        if (stringifyMetadata)
            o.put("metadata", new JSONObject(getMetadata()).toString());

        return o;
    }
}
