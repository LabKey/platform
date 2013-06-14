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
package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 5/17/13
 * Time: 12:35 PM
 */
public class FormElement
{
    private String _xtype;
    private ColumnInfo _boundCol;

    private FormElement()
    {

    }

    public static FormElement createForColumn(ColumnInfo col)
    {
        FormElement fd = new FormElement();
        fd._boundCol = col;

        return fd;
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = new JSONObject();

        DisplayColumn dc = _boundCol.getDisplayColumnFactory().createRenderer(_boundCol);
        json.putAll(JsonWriter.getMetaData(dc, null, true, true, true));

        json.put("schemaName", _boundCol.getParentTable().getPublicSchemaName());
        json.put("queryName", _boundCol.getParentTable().getPublicName());

        return json;
    }
}
