package org.labkey.visualization.sql;

import org.json.JSONArray;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
* Copyright (c) 2011 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* <p/>
* User: brittp
* Date: Jan 27, 2011 11:13:33 AM
*/
public class VisualizationSourceColumn
{
    private String _queryName;
    private UserSchema _schema;
    private String _name;
    private JdbcType _type = null;
    private Set<Object> _values = new LinkedHashSet<Object>();

    public VisualizationSourceColumn(UserSchema schema, String queryName, String name)
    {
        _name = name;
        _queryName = queryName;
        _schema = schema;
    }

    public VisualizationSourceColumn(ViewContext context, Map<String, Object> properties)
    {
        this(getUserSchema(context, (String) properties.get("schemaName")), (String) properties.get("queryName"), (String) properties.get("name"));
        JSONArray values = (JSONArray) properties.get("values");
        if (values != null)
        {
            for (int i = 0; i < values.length(); i++)
                _values.add(values.get(i));
        }
    }

    private static UserSchema getUserSchema(ViewContext context, String schemaName)
    {
        DefaultSchema defSchema = DefaultSchema.get(context.getUser(), context.getContainer());
        return (UserSchema) defSchema.getSchema(schemaName);
    }

    public String getSchemaName()
    {
        return _schema.getName();
    }

    public UserSchema getSchema()
    {
        return _schema;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public String getOriginalName()
    {
        return _name;
    }

    public String getSelectName()
    {
        String[] parts = _name.split("[\\./]");
        StringBuilder selectName = new StringBuilder();
        String sep = "";
        for (String part : parts)
        {
            selectName.append(sep);
            String identifier = _schema.getDbSchema().getSqlDialect().makeLegalIdentifier(part);
            if (identifier.charAt(0) == '"')
                selectName.append(identifier);
            else
                selectName.append("\"").append(identifier).append("\"");
            sep = ".";
        }
        return selectName.toString();
    }

    public JdbcType getType() throws VisualizationSQLGenerator.GenerationException
    {
        if (_type == null)
        {
            try
            {
                TableInfo tinfo = _schema.getTable(_queryName);
                if (tinfo == null)
                {
                    throw new VisualizationSQLGenerator.GenerationException("Unable to find table " + _schema.getName() + "." + _queryName +
                            ".  The table may not exist, or you may not have permissions to read the data.");
                }

                ColumnInfo column = tinfo.getColumn(_name);
                if (column == null)
                {
                    throw new VisualizationSQLGenerator.GenerationException("Unable to find field " + _name + " in " + _schema.getName() + "." + _queryName +
                            ".  The field may not exist, or you may not have permissions to read the data.");
                }

                _type = column.getJdbcType();
            }
            catch (QueryParseException e)
            {
                throw new VisualizationSQLGenerator.GenerationException("Unable to determine datatype for field " + _name + " in " + _schema.getName() + "." + _queryName + 
                        ".  The data may not exist, or you may not have permissions to read the data.", e);
            }
        }
        return _type;
    }

    public Set<Object> getValues()
    {
        return _values;
    }

    public String getAlias()
    {
        return ColumnInfo.legalNameFromName(getSchemaName() + "_" + _queryName + "_" + _name);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VisualizationSourceColumn that = (VisualizationSourceColumn) o;

        if (!_name.equals(that._name)) return false;
        if (!_queryName.equals(that._queryName)) return false;
        if (!getSchemaName().equals(that.getSchemaName())) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = getSchemaName().hashCode();
        result = 31 * result + _queryName.hashCode();
        result = 31 * result + _name.hashCode();
        return result;
    }
}
