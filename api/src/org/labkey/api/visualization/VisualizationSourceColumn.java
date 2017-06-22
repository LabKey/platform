/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.visualization;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 27, 2011 11:13:33 AM
 */
public class VisualizationSourceColumn
{
    private String _queryName;
    private UserSchema _schema;
    private boolean _allowNullResults;
    private boolean _inNotNullSet;
    private boolean _requireLeftJoin;
    private String _name;
    protected String _alias;
    protected String _clientAlias;
    private String _otherAlias;
    protected String _label;
    private JdbcType _type = null;
    private boolean _hidden = false;
    private Set<Object> _values = new LinkedHashSet<>();

    // used by CDS, not used by VisualizationSQLGenerator, just copy it around please
    private String _axisName = null;


    public Map<String, String> toJSON()
    {
        Map<String, String> info = new HashMap<>();
        info.put("measureName", getOriginalName());
        if (StringUtils.isNotEmpty(getClientAlias()))
            info.put("alias", getClientAlias());
        info.put("columnName", getAlias());
        info.put("schemaName", getSchemaName());
        info.put("queryName", getQueryName());
        if (StringUtils.isNotEmpty(_axisName))
            info.put("axisName",_axisName);

        return info;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public void setHidden(boolean hidden)
    {
        _hidden = hidden;
    }

    public static class Factory
    {
        private Map<Path, VisualizationSourceColumn> _currentCols = new HashMap<>();
        private Map<String,VisualizationSourceColumn> _aliasMap = new CaseInsensitiveHashMap<>();

        private VisualizationSourceColumn findOrAdd(VisualizationSourceColumn col)
        {
            col.ensureColumn();

            Path key = new Path(col.getSchemaName(), col.getQueryName(), col.getOriginalName());
            VisualizationSourceColumn current = _currentCols.get(key);
            if (current != null)
            {
                // do any necessary merging:
                if (!col.isAllowNullResults())
                    current.setAllowNullResults(false);
                if (!current._values.isEmpty() && !col._values.isEmpty() && !col._values.equals(current._values))
                    throw new IllegalStateException("multiple values array specified for column " + col.getOriginalName());
                current._values.addAll(col._values);
                if (null != col.getClientAlias() && null == current.getClientAlias())
                    current._clientAlias = col.getClientAlias();
                if (null != col._axisName && null == current._axisName) // this happens because of addExtraSelectColumns()
                    current._axisName = col._axisName;
                return current;
            }
            else
            {
                _currentCols.put(key, col);
                if (null != col.getAlias())
                    _aliasMap.put(col.getAlias(), col);
                return col;
            }
        }

        // Consider: Builder?
        public VisualizationSourceColumn create(UserSchema schema, String queryName, String name, Boolean allowNullResults)
        {
            VisualizationSourceColumn col = new VisualizationSourceColumn(schema, queryName, name, allowNullResults, false);
            return findOrAdd(col);
        }

        public VisualizationSourceColumn create(ViewContext context, VisDataRequest.Measure measure)
        {
            VisualizationSourceColumn col = new VisualizationSourceColumn(context, measure);
            return findOrAdd(col);
        }

        public VisualizationSourceColumn create(UserSchema schema, String queryName, String name, Boolean allowNullResults, VisDataRequest.DateOptions dateOptions)
        {
            String encodedQueryName = queryName + "-" + (dateOptions.isUseProtocolDay() ? "true" : "false") + "-" + dateOptions.getZeroDayVisitTag();
            if (dateOptions.getAltQueryName() != null)
                encodedQueryName += "-" + dateOptions.getAltQueryName();

            VisualizationSourceColumn col = new VisualizationSourceColumn(schema, encodedQueryName, name, allowNullResults, false);
            return findOrAdd(col);
        }

        public VisualizationSourceColumn getByAlias(String alias)
        {
            return _aliasMap.get(alias);
        }
    }

    protected VisualizationSourceColumn(UserSchema schema, String queryName, String name, Boolean allowNullResults, Boolean requireLeftJoin)
    {
        _name = name;
        _queryName = queryName;
        if (schema == null)
        {
            throw new IllegalArgumentException("No schema supplied, queryName is: '" + queryName + "'");
        }
        _schema = schema;
        _allowNullResults = allowNullResults == null || allowNullResults;
        _requireLeftJoin = (requireLeftJoin == null ? false : requireLeftJoin);
        _inNotNullSet = false;
    }

    protected VisualizationSourceColumn(ViewContext context, VisDataRequest.Measure measure)
    {
        this(getUserSchema(context, measure.getSchemaName()),
                measure.getQueryName(),
                measure.getName(),
                measure.getAllowNullResults(),
                measure.getRequireLeftJoin());
        _inNotNullSet = BooleanUtils.toBooleanDefaultIfNull(measure.getInNotNullSet(), false);

        if (StringUtils.isNotEmpty(measure.getAxisName()))
            _axisName = measure.getAxisName();

        List<Object> values = measure.getValues();
        _clientAlias = measure.getAlias();
        if (values != null)
        {
            _values.addAll(values);
        }
        String namedSetValue = measure.getNsvalues();
        if (namedSetValue != null)
        {
            List<String> namedSet = QueryService.get().getNamedSet(namedSetValue);
            _values.addAll(namedSet);
        }
    }

    private static UserSchema getUserSchema(ViewContext context, String schemaName)
    {
        if (schemaName == null)
        {
            throw new NullPointerException("No schema specified");
        }
        UserSchema result = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
        if (result == null)
        {
            throw new NotFoundException("No schema found with name '" + schemaName + "'");
        }
        return result;
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

    public String getClientAlias()
    {
        return _clientAlias;
    }

    public String getAxisName()
    {
        return _axisName;
    }

    public boolean isAllowNullResults()
    {
        return _allowNullResults;
    }

    public void setAllowNullResults(boolean allowNullResults)
    {
        _allowNullResults = allowNullResults;
    }

    public boolean isRequireLeftJoin()
    {
        return _requireLeftJoin;
    }

    public void setRequireLeftJoin(boolean requireLeftJoin)
    {
        _requireLeftJoin = requireLeftJoin;
    }

    public boolean isInNotNullSet()
    {
        return _inNotNullSet;
    }

    public void setInNotNullSet(boolean inNotNullSet)
    {
        _inNotNullSet = inNotNullSet;
    }

    public void ensureColumn() throws IllegalArgumentException
    {
        if (getSchemaName() == null || getQueryName() == null || getOriginalName() == null)
        {
            throw new IllegalArgumentException("SchemaName, queryName, and name are all required for each measure, dimension, or sort.");
        }

        try
        {
            findColumnInfo();
        }
        catch (SQLGenerationException e)
        {
            throw new NotFoundException(e.getMessage(), e);
        }
    }

    public String getSelectName()
    {
        try
        {
            ColumnInfo columnInfo = findColumnInfo();
            List<String> parts = columnInfo.getFieldKey().getParts();

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
        catch (SQLGenerationException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public String getLabel()
    {
        try
        {
            _getTypeAndLabel();
            return _label;
        }
        catch (SQLGenerationException x)
        {
            return null;
        }
    }

    public JdbcType getType() throws SQLGenerationException
    {
        _getTypeAndLabel();
        return _type;
    }

    private void _getTypeAndLabel() throws SQLGenerationException
    {
        if (null == _label && null == _type)
        {
            try
            {
                ColumnInfo column = findColumnInfo();
                _type = column.getJdbcType();
                _label = column.getLabel();
            }
            catch (QueryParseException e)
            {
                throw new SQLGenerationException("Unable to determine datatype for field " + _name + " in " + _schema.getName() + "." + _queryName + 
                        ".  The data may not exist, or you may not have permissions to read the data.", e);
            }
        }
    }

    @NotNull
    public ColumnInfo getColumnInfo()
    {
        try
        {
            return findColumnInfo();
        }
        catch (SQLGenerationException e)
        {
            throw new NotFoundException(e.getMessage(), e);
        }
    }

    ColumnInfo _columnInfo;

    @NotNull
    private ColumnInfo findColumnInfo() throws SQLGenerationException
    {
        if (null == _columnInfo)
        {
            if (getSchemaName() == null || getQueryName() == null || getOriginalName() == null)
            {
                throw new IllegalArgumentException("SchemaName, queryName, and name are all required for each measure, dimension, or sort.");
            }
            TableInfo tinfo = _schema.getTable(_queryName);
            if (tinfo == null)
            {
                throw new SQLGenerationException("Unable to find table " + _schema.getName() + "." + _queryName +
                        ".  The table may have been deleted, or you may not have permissions to read the data.");
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
            _columnInfo = column;
            if (null == _columnInfo)
            {
                throw new NotFoundException("Unable to find field " + getOriginalName() + " in " + getSchemaName() + "." + getQueryName() +
                        ".  The field may have been deleted, renamed, or you may not have permissions to read the data.");
            }

        }
        return _columnInfo;
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


    public String getSQLAlias()
    {
        return "\"" + getAlias() + "\"";
    }

    public String getSQLOther()
    {
        return "\"" + getOtherAlias() + "\"";
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
        if (null == _alias)
        {
            _alias = getAlias(getSchemaName(), _queryName, _name);
        }
        return _alias;
    }

    public static String getAlias(String schemaName, String queryName, String name)
    {
        return (schemaName + "_" + queryName + "_" + name).replaceAll("/","_");
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
