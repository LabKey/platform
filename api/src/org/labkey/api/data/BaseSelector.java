/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.labkey.api.collections.ArrayListMap;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseSelector implements Selector
{
    private final DbScope _scope;
    private Connection _conn = null;
    private SQLFragment _sql = null;
    private ExceptionFramework _exceptionFramework = ExceptionFramework.Spring;

    abstract SQLFragment getSql();

    protected BaseSelector(DbScope scope)
    {
        _scope = scope;
    }

    public Connection getConnection() throws SQLException
    {
        return _scope.getConnection();
    }

    public DbScope getScope()
    {
        return _scope;
    }

    protected void setExceptionFramework(ExceptionFramework exceptionFramework)
    {
        _exceptionFramework = exceptionFramework;
    }

    @Override
    public ResultSet getResultSet() throws SQLException
    {
        _sql = getSql();
        _conn = getConnection();
        return Table._executeQuery(_conn, _sql.getSQL(), _sql.getParamsArray());
    }

    private <K> ArrayList<K> getArrayList(final Class<K> clazz)
    {
        final ArrayList<K> list;
        final Table.Getter getter = Table.Getter.forClass(clazz);

        // This is a simple object (Number, String, Date, etc.)
        if (null != getter)
        {
            list = new ArrayList<K>();
            forEach(new ForEachBlock<ResultSet>() {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    //noinspection unchecked
                    list.add((K)getter.getObject(rs));
                }
            });
        }
        else
        {
            list = handleResultSet(new ResultSetHandler<ArrayList<K>>() {
                @Override
                public ArrayList<K> handle(ResultSet rs) throws SQLException
                {
                    if (Map.class.isAssignableFrom(clazz))
                    {
                        CachedResultSet copy = (CachedResultSet) Table.cacheResultSet(getScope().getSqlDialect(), rs, Table.ALL_ROWS, null);
                        //noinspection unchecked
                        K[] arrayListMaps = (K[])(copy._arrayListMaps == null ? new ArrayListMap[0] : copy._arrayListMaps);
                        copy.close();

                        // TODO: Not very efficient...
                        return new ArrayList<K>(Arrays.asList(arrayListMaps));
                    }
                    else
                    {
                        ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(clazz);

                        if (null == factory)
                            throw new IllegalArgumentException("Cound not find object factory for " + clazz.getSimpleName() + ".");

                        return factory.handleArrayList(rs);
                    }
                }
            });
        }

        return list;
    }

    @Override
    public <K> K[] getArray(Class<K> clazz)
    {
        ArrayList<K> list = getArrayList(clazz);
        return list.toArray((K[]) Array.newInstance(clazz, list.size()));
    }

    @Override
    public <K> Collection<K> getCollection(Class<K> clazz)
    {
        return getArrayList(clazz);
    }

    @Override
    public <K> K getObject(Class<K> clazz)  // TODO: Or getSingleton?  Or getSingleObject?
    {
        List<K> list = getArrayList(clazz);

        if (list.size() == 1)
            return list.get(0);
        else if (list.isEmpty())
            return null;
        else
            throw new IllegalStateException("Query returned " + list.size() + " " + clazz.getSimpleName() + " objects; expected 1 or 0.");
    }

    @Override
    public void forEach(final ForEachBlock<ResultSet> block)
    {
        handleResultSet((new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException
            {
                while (rs.next())
                    block.exec(rs);

                return null;
            }
        }));
    }

    @Override
    public void forEachMap(final ForEachBlock<Map<String, Object>> block)
    {
        handleResultSet(new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException
            {
                ResultSetIterator iter = new ResultSetIterator(rs);

                while (iter.hasNext())
                    block.exec(iter.next());

                return null;
            }
        });
    }

    @Override
    public <K> void forEach(final ForEachBlock<K> block, Class<K> clazz)
    {
        final ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(clazz);

        ForEachBlock<Map<String, Object>> mapBlock = new ForEachBlock<Map<String, Object>>() {
            @Override
            public void exec(Map<String, Object> map) throws SQLException
            {
                block.exec(factory.fromMap(map));
            }
        };

        forEachMap(mapBlock);
    }

    @Override
    public Map<Object, Object> fillValueMap(final Map<Object, Object> map)
    {
        forEach(new ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                map.put(rs.getObject(1), rs.getObject(2));
            }
        });

        return map;
    }

    @Override
    public Map<Object, Object> getValueMap()
    {
        return fillValueMap(new HashMap<Object, Object>());
    }

    private <K> K handleResultSet(ResultSetHandler<K> handler)
    {
        ResultSet rs = null;

        try
        {
            rs = getResultSet();
            return handler.handle(rs);
        }
        catch(SQLException e)
        {
            // TODO: Substitute SQL parameters placeholders with values?
            Table.doCatch(_sql.getSQL(), _sql.getParamsArray(), _conn, e);
            throw _exceptionFramework.translate(getScope(), "Message", _sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            Table.doFinally(rs, null, _conn, getScope());
        }
    }

    interface ResultSetHandler<K>
    {
        K handle(ResultSet rs) throws SQLException;
    }
}
