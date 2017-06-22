/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: matthew
 * Date: 5/11/13
 * Time: 7:31 AM
 */
public class QueryDataIteratorBuilder implements DataIteratorBuilder
{
    final Container _container;
    final User _user;
    final SchemaKey _schemaKey;
    final QuerySchema _schema;
    ContainerFilter _containerFilter;
    List<String> _columns;

    final Map<String,Object> _parameters = new CaseInsensitiveHashMap<>();
    final String _queryName;
    final String _sql;
    final SimpleFilter _filter;


    public QueryDataIteratorBuilder(Container c, User u, SchemaKey schema, String query, String sql, SimpleFilter f)
    {
        if (null != query && null != sql)
            throw new IllegalArgumentException("Specify SQL or query name not both");

        _container = c;
        _user = u;
        _schemaKey = schema;
        _schema = null;

        _queryName = query;
        _sql = sql;
        _filter = f;
    }


    public QueryDataIteratorBuilder(QuerySchema schema, String query, String sql, SimpleFilter f)
    {
        if (null != query && null != sql)
            throw new IllegalArgumentException("Specify SQL or query name not both");

        _schema = schema;
        _container = _schema.getContainer();
        _user = _schema.getUser();
        _schemaKey = ((UserSchema)_schema).getSchemaPath();

        _queryName = query;
        _sql = sql;
        _filter = f;
    }


    public void setParameters(Map<String,Object> p)
    {
        _parameters.putAll(p);
    }

    public void setContainerFilter(String containerFilterName)
    {
        _containerFilter = ContainerFilter.getContainerFilterByName(containerFilterName, _user);
    }

    public void setColumns(List<String> columns)
    {
        _columns = columns;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        QuerySchema s = null!=_schema ? _schema : DefaultSchema.get(_user,_container, _schemaKey);
        if (null == s || null == s.getDbSchema())
        {
            context.getErrors().addRowError(new ValidationException("Schema not found: " + _schemaKey));
            return null;
        }

        String sql;
        if (null != _queryName)
            sql = "SELECT * FROM " + s.getDbSchema().getSqlDialect().quoteIdentifier(_queryName);
        else
            sql = _sql;

        QueryService qs = QueryService.get();
        QueryDefinition qd;
        if (_schema != null)
            qd = qs.createQueryDef(_user, _container, (UserSchema)_schema, "source");
        else
            qd = qs.createQueryDef(_user, _container, _schemaKey, "source");

        qd.setSql(sql);
        ArrayList<QueryException> qerrors = new ArrayList<>();
        TableInfo t = qd.getTable(qerrors, true);

        Collection<ColumnInfo> selectCols = t.getColumns();
        if (null != _columns && !_columns.isEmpty())
        {
            List<FieldKey> keys = new ArrayList<>();
            _columns.forEach(x -> keys.add(FieldKey.fromString(x)));

            selectCols = qs.getColumns(t, keys).values();
        }

        if (!qerrors.isEmpty())
        {
            context.getErrors().addRowError(new ValidationException(qerrors.get(0).getMessage()));
            return null;
        }

        if (null != _containerFilter && t instanceof ContainerFilterable && t.supportsContainerFilter())
        {
            ((ContainerFilterable) t).setContainerFilter(_containerFilter);
        }

        try
        {
            ResultSet rs = qs.select(t, selectCols, _filter, null, _parameters, false);
            return new ResultSetDataIterator(rs, context);
        }
        catch (QueryParseException x)
        {
            context.getErrors().addRowError(new ValidationException("Error parsing query: ", x.getMessage()));
        }
        return null;
    }
}
