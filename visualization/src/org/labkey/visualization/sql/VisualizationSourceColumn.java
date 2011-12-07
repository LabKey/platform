package org.labkey.visualization.sql;

import org.json.JSONArray;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;

import java.util.*;

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
    private boolean _allowNullResults;
    private String _name;
    private String _otherAlias;
    private JdbcType _type = null;
    private Set<Object> _values = new LinkedHashSet<Object>();

    public static class Factory
    {
        private Map<String, VisualizationSourceColumn> _currentCols = new HashMap<String, VisualizationSourceColumn>();

        private VisualizationSourceColumn findOrAdd(VisualizationSourceColumn col)
        {
            String key = ColumnInfo.legalNameFromName(col.getSchemaName() + "_" + col.getQueryName() + "_" + col.getOriginalName());
            VisualizationSourceColumn current = _currentCols.get(key);
            if (current != null)
            {
                // do any necessary merging:
                if (!col.isAllowNullResults())
                    current.setAllowNullResults(false);
                return current;
            }
            else
            {
                _currentCols.put(key, col);
                return col;
            }
        }

        public VisualizationSourceColumn create(UserSchema schema, String queryName, String name, Boolean allowNullResults)
        {
            VisualizationSourceColumn col = new VisualizationSourceColumn(schema, queryName, name, allowNullResults);
            return findOrAdd(col);
        }

        public VisualizationSourceColumn create(ViewContext context, Map<String, Object> properties)
        {
            VisualizationSourceColumn col = new VisualizationSourceColumn(context, properties);
            return findOrAdd(col);
        }

        public VisualizationSourceColumn get(String columnKey)
        {
            return _currentCols.get(columnKey);
        }
    }

    protected VisualizationSourceColumn(UserSchema schema, String queryName, String name, Boolean allowNullResults)
    {
        _name = name;
        _queryName = queryName;
        _schema = schema;
        _allowNullResults = allowNullResults == null || allowNullResults;
    }

    protected VisualizationSourceColumn(ViewContext context, Map<String, Object> properties)
    {
        this(getUserSchema(context, (String) properties.get("schemaName")), (String) properties.get("queryName"), (String) properties.get("name"), (Boolean) properties.get("allowNullResults"));
        JSONArray values = (JSONArray) properties.get("values");
        if (values != null)
        {
            for (int i = 0; i < values.length(); i++)
                _values.add(values.get(i));
        }
    }

    private static UserSchema getUserSchema(ViewContext context, String schemaName)
    {
        if (schemaName == null)
        {
            throw new NullPointerException("No schema specified");
        }
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

    public boolean isAllowNullResults()
    {
        return _allowNullResults;
    }

    public void setAllowNullResults(boolean allowNullResults)
    {
        _allowNullResults = allowNullResults;
    }

    public String getSelectName()
    {
        try
        {
            ColumnInfo columnInfo = findColumnInfo();
            List<String> parts;
            if (columnInfo != null)
            {
                // We found the column
                parts = columnInfo.getFieldKey().getParts();
            }
            else
            {
                // The column can't be found, but try to select it anyway to give a reasonable error to the user
                parts = Arrays.asList(_name.split("[\\./]"));
            }

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
        catch (VisualizationSQLGenerator.GenerationException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public JdbcType getType() throws VisualizationSQLGenerator.GenerationException
    {
        if (_type == null)
        {
            try
            {
                ColumnInfo column = findColumnInfo();
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

    private ColumnInfo findColumnInfo() throws VisualizationSQLGenerator.GenerationException
    {
        TableInfo tinfo = _schema.getTable(_queryName);
        if (tinfo == null)
        {
            throw new VisualizationSQLGenerator.GenerationException("Unable to find table " + _schema.getName() + "." + _queryName +
                    ".  The table may not exist, or you may not have permissions to read the data.");
        }

        FieldKey fieldKey = FieldKey.fromString(_name);
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(tinfo, Collections.singleton(fieldKey));
        ColumnInfo column = cols.get(fieldKey);
        if (column == null && _name.contains("."))
        {
            fieldKey = FieldKey.fromParts(_name.split("[\\./]"));
            cols = QueryService.get().getColumns(tinfo, Collections.singleton(fieldKey));
            column = cols.get(fieldKey);
        }
        return column;
    }

    public Set<Object> getValues()
    {
        return _values;
    }

    public void syncValues(VisualizationSourceColumn other)
    {
        _values.addAll(other.getValues());
        other._values.addAll(getValues());
    }

    public void setOtherAlias(String otherAlias)
    {
        _otherAlias = otherAlias;
    }

    public String getOtherAlias()
    {
        if (_otherAlias == null)
            return getAlias();
        else
            return _otherAlias;
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
