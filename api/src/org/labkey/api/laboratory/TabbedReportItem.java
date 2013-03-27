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
package org.labkey.api.laboratory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 1/13/13
 * Time: 12:51 PM
 */
public class TabbedReportItem extends AbstractNavItem
{
    private String _name;
    private String _label;
    private String _category;
    private String _schemaName;
    private String _queryName;
    private DataProvider _provider;
    private boolean _visible = true;

    private FieldKey _subjectIdFieldKey = null;
    private FieldKey _sampleDateFieldKey = null;

    public TabbedReportItem(DataProvider provider, String schemaName, String queryName, String label, String category)
    {
        _provider = provider;
        _name = queryName;
        _label = label;
        _schemaName = schemaName;
        _queryName = queryName;
        _category = category;

    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getCategory()
    {
        return _category;
    }

    public DataProvider getDataProvider()
    {
        return _provider;
    }

    public String getRendererName()
    {
        return "";
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return _visible;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);

        UserSchema us = QueryService.get().getUserSchema(u, c, getSchemaName());
        if (us == null)
            return null;

        QueryDefinition qd = us.getQueryDefForTable(getQueryName());
        if (qd == null)
            return null;

        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo ti = qd.getTable(errors, true);
        if (ti == null)
        {
            return null;
        }

        for (ColumnInfo ci : ti.getColumns())
        {
            if (LaboratoryService.PARTICIPANT_CONCEPT_URI.equalsIgnoreCase(ci.getConceptURI()))
            {
                json.put("subjectFieldName", ci.getFieldKey());
            }
            else if (LaboratoryService.SAMPLEDATE_CONCEPT_URI.equalsIgnoreCase(ci.getConceptURI()))
            {
                json.put("dateFieldName", ci.getFieldKey());
            }
        }

        if (_subjectIdFieldKey != null)
            json.put("subjectFieldName", _subjectIdFieldKey);

        if (_sampleDateFieldKey != null)
            json.put("dateFieldName", _sampleDateFieldKey);

        ColumnInfo overlappingCol = ti.getColumn("overlappingProjectsPivot");
        if (overlappingCol != null)
        {
            json.put("overlappingProjectsFieldName", overlappingCol.getFieldKey().toString());
            json.put("overlappingProjectsFieldKeyArray", new JSONArray(overlappingCol.getFieldKey().getParts()));
        }

        ColumnInfo allProjectsCol = ti.getColumn("allProjectsPivot");
        if (allProjectsCol != null)
        {
            json.put("allProjectsFieldName", allProjectsCol.getFieldKey().toString());
            json.put("allProjectsFieldKeyArray", new JSONArray(allProjectsCol.getFieldKey().getParts()));
        }

        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());
        json.put("reportType", "query");

        return json;
    }

    public FieldKey getSubjectIdFieldKey()
    {
        return _subjectIdFieldKey;
    }

    public void setSubjectIdFieldKey(FieldKey subjectIdFieldKey)
    {
        _subjectIdFieldKey = subjectIdFieldKey;
    }

    public FieldKey getSampleDateFieldKey()
    {
        return _sampleDateFieldKey;
    }

    public void setSampleDateFieldKey(FieldKey sampleDateFieldKey)
    {
        _sampleDateFieldKey = sampleDateFieldKey;
    }

    public void setVisible(boolean visible)
    {
        _visible = visible;
    }
}
