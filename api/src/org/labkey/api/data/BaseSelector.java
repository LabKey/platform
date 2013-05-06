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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.RowMap;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: 12/11/12
 * Time: 5:29 AM
 */

// A partial, base implementation of Selector. This class manipulates result sets but doesn't generate them. Subclasses
// include ExecutingSelector (which executes SQL to generate a result set) and (in the future) ResultSetSelector, which
// will take an externally generated ResultSet (e.g., from JDBC metadata calls) and performSelector operations on it.
public abstract class BaseSelector extends JdbcCommand implements Selector
{
    protected BaseSelector(@NotNull DbScope scope, @Nullable Connection conn)
    {
        super(scope, conn);
    }

    // Used by standard enumerating methods (forEach(), getArrayList()) and their callers (getArray(), getCollection(), getObject())
    abstract protected ResultSetFactory getStandardResultSetFactory();

    // No implementation of getResultSet(), getRowCount(), or exists() here since implementations will differ widely.

    @Override
    public <K> K[] getArray(Class<K> clazz)
    {
        ArrayList<K> list = getArrayList(clazz);
        //noinspection unchecked
        return list.toArray((K[]) Array.newInstance(clazz, list.size()));
    }

    // Convenience method that avoids "unchecked assignment" warnings
    @Override
    public Map<String, Object>[] getMapArray()
    {
        //noinspection unchecked
        return getArray(Map.class);
    }

    @Override
    public <K> Collection<K> getCollection(Class<K> clazz)
    {
        return getArrayList(clazz);
    }

    @Override
    public <K> ArrayList<K> getArrayList(Class<K> clazz)
    {
        return getArrayList(clazz, getStandardResultSetFactory());
    }

    protected <K> ArrayList<K> getArrayList(final Class<K> clazz, ResultSetFactory factory)
    {
        final ArrayList<K> list;
        final Table.Getter getter = Table.Getter.forClass(clazz);

        // If we have a Getter, then use it (simple object case: Number, String, Date, etc.)
        if (null != getter)
        {
            list = new ArrayList<>();
            forEach(new ForEachBlock<ResultSet>() {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    //noinspection unchecked
                    list.add((K)getter.getObject(rs));
                }
            }, factory);
        }
        // If not, we're generating maps or beans
        else
        {
            list = handleResultSet(factory, new ResultSetHandler<ArrayList<K>>()
            {
                @Override
                public ArrayList<K> handle(ResultSet rs, Connection conn) throws SQLException
                {
                    if (Map.class == clazz)
                    {
                        // We will consume the result set and close it immediately, so no need to cache meta data
                        CachedResultSet copy = (CachedResultSet) Table.cacheResultSet(rs, false, Table.ALL_ROWS, null);
                        //noinspection unchecked
                        K[] arrayListMaps = (K[]) (copy._arrayListMaps == null ? new ArrayListMap[0] : copy._arrayListMaps);
                        copy.close();

                        // TODO: Not very efficient...
                        ArrayList<K> list = new ArrayList<>(arrayListMaps.length);
                        //noinspection unchecked
                        Collections.addAll(list, arrayListMaps);
                        return list;
                    }
                    else
                    {
                        ObjectFactory<K> factory = getObjectFactory(clazz);

                        return factory.handleArrayList(rs);
                    }
                }
            });
        }

        return list;
    }

    public <K> K getObject(Class<K> clazz)
    {
        return getObject(getArrayList(clazz), clazz);
    }

    protected <K> K getObject(Class<K> clazz, ResultSetFactory factory)
    {
        return getObject(getArrayList(clazz, factory), clazz);
    }

    protected <K> K getObject(List<K> list, Class<K> clazz)
    {
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
        forEach(block, getStandardResultSetFactory());
    }

    protected void forEach(final ForEachBlock<ResultSet> block, ResultSetFactory factory)
    {
        handleResultSet(factory, (new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs, Connection conn) throws SQLException
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
        handleResultSet(getStandardResultSetFactory(), new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs, Connection conn) throws SQLException
            {
                ResultSetIterator iter = new ResultSetIterator(rs);

                while (iter.hasNext())
                    block.exec(iter.next());

                return null;
            }
        });
    }

    public interface ResultSetHandler<K>
    {
        K handle(ResultSet rs, Connection conn) throws SQLException;
    }

    protected <K> K handleResultSet(ResultSetFactory factory, ResultSetHandler<K> handler)
    {
        boolean success = false;
        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = getConnection();
            rs = factory.getResultSet(conn);

            K ret = handler.handle(rs, conn);
            success = true;

            return ret;
        }
        catch(SQLException e)
        {
            factory.handleSqlException(e, conn);
            throw new IllegalStateException(factory.getClass().getSimpleName() + ".handleSqlException() should have thrown an exception");
        }
        finally
        {
            if (factory.shouldClose() || !success)
                close(rs, conn);

            afterComplete(rs);
        }
    }

    @Override
    public <K> void forEach(final ForEachBlock<K> block, Class<K> clazz)
    {
        final Table.Getter getter = Table.Getter.forClass(clazz);

        // This is a simple object (Number, String, Date, etc.)
        if (null != getter)
        {
            forEach(new ForEachBlock<ResultSet>() {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    //noinspection unchecked
                    block.exec((K)getter.getObject(rs));
                }
            });
        }
        else
        {
            final ObjectFactory<K> factory = getObjectFactory(clazz);

            ForEachBlock<Map<String, Object>> mapBlock = new ForEachBlock<Map<String, Object>>() {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    block.exec(factory.fromMap(map));
                }
            };

            forEachMap(mapBlock);
        }
    }

    protected <K> ObjectFactory<K> getObjectFactory(Class<K> clazz)
    {
        ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(clazz);

        if (null == factory)
            throw new IllegalArgumentException("Could not find object factory for " + clazz.getSimpleName() + ".");

        return factory;
    }

    @Override
    public <K, V> Map<K, V> fillValueMap(final Map<K, V> fillMap)
    {
        // Standard map enumeration ensures that standard type conversion happenes (vs. ResultSet enumeration and rs.getObject())
        forEachMap(new ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map<String, Object> map) throws SQLException
            {
                RowMap rowMap = (RowMap)map;
                //noinspection unchecked
                fillMap.put((K)rowMap.get(1), (V)rowMap.get(2));
            }
        });

        return fillMap;
    }

    @Override
    public <K, V> Map<K, V> getValueMap()
    {
        return fillValueMap(new HashMap<K, V>());
    }
}
