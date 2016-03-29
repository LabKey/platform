/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.dataentry.AbstractFormSection;
import org.labkey.api.ehr.dataentry.FormElement;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 10:54 AM
 */
public class SimpleFormSection extends AbstractFormSection
{
    private String _schemaName;
    private String _queryName;

    protected boolean _showLocation = false;
    protected boolean _allowRowEditing = true;

    public SimpleFormSection(String schemaName, String queryName, String label, String xtype)
    {
        this(schemaName, queryName, label, xtype, EHRService.FORM_SECTION_LOCATION.Body);
    }

    public SimpleFormSection(String schemaName, String queryName, String label, String xtype, EHRService.FORM_SECTION_LOCATION location)
    {
        super(queryName, label, xtype, location);
        _schemaName = schemaName;
        _queryName = queryName;
    }

    protected void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    @Override
    public Set<Pair<String, String>> getTableNames()
    {
        Set<Pair<String, String>> tables = new HashSet<>();
        tables.add(Pair.of(_schemaName, _queryName));
        return tables;
    }

    @Override
    public JSONObject toJSON(DataEntryFormContext ctx, boolean includeFormElements)
    {
        JSONObject json = super.toJSON(ctx, includeFormElements);

        JSONArray queries = new JSONArray();

        JSONObject q = new JSONObject();
        q.put("schemaName", _schemaName);
        q.put("queryName", _queryName);
        queries.put(q);

        json.put("queries", queries);
        json.put("allowRowEditing", _allowRowEditing);

        return json;
    }

    protected List<FieldKey> getFieldKeys(TableInfo ti)
    {
        List<FieldKey> keys = EHRService.get().getDefaultFieldKeys(ti);
        if (_showLocation)
        {
            keys.add(0, FieldKey.fromString("Id/curLocation/location"));
        }

        return keys;
    }

    @Override
    protected List<FormElement> getFormElements(DataEntryFormContext ctx)
    {
        List<FormElement> list = new ArrayList<>();
        for (TableInfo ti : getTables(ctx))
        {
            List<FieldKey> keys = getFieldKeys(ti);

            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, keys);
            for (FieldKey key : keys)
            {
                if (!cols.containsKey(key))
                {
                    _log.error("Unable to find field: " +  key.toString() + " on table " + ti.getPublicSchemaName() + "." + ti.getPublicName());
                    continue;
                }

                list.add(FormElement.createForColumn(cols.get(key)));
            }
        }

        return list;
    }
}
