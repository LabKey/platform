/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseSelector<FACTORY extends SqlFactory> extends JdbcCommand implements Selector
{
    abstract FACTORY getSqlFactory();  // A single query's SQL factory; this allows better Selector reuse, since query-specific
                                       // optimizations won't mutate the Selector's externally set state.

    protected BaseSelector(DbScope scope)
    {
        super(scope, null);
    }

    //@Override      // TODO: Not valid to call at the moment since connection never gets closed
    public ResultSet getResultSet() throws SQLException
    {
        return getSqlFactory().handleResultSet(null);  // NYI
    }

    private <K> ArrayList<K> getArrayList(final Class<K> clazz, FACTORY factory)
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
            }, factory);
        }
        else
        {
            list = factory.handleResultSet(new ResultSetHandler<ArrayList<K>>()
            {
                @Override
                public ArrayList<K> handle(ResultSet rs) throws SQLException
                {
                    if (Map.class == clazz)
                    {
                        CachedResultSet copy = (CachedResultSet) Table.cacheResultSet(getScope().getSqlDialect(), rs, Table.ALL_ROWS, null);
                        //noinspection unchecked
                        K[] arrayListMaps = (K[]) (copy._arrayListMaps == null ? new ArrayListMap[0] : copy._arrayListMaps);
                        copy.close();

                        // TODO: Not very efficient...
                        ArrayList<K> list = new ArrayList<K>(arrayListMaps.length);
                        Collections.addAll(list, arrayListMaps);
                        return list;
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
    public long getRowCount()
    {
        return getRowCount(getSqlFactory());
    }

    protected long getRowCount(FACTORY factory)
    {
        SqlFactory rowCountFactory = new RowCountSqlFactory(factory);

        return rowCountFactory.handleResultSet(new ResultSetHandler<Long>()
        {
            @Override
            public Long handle(ResultSet rs) throws SQLException
            {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    @Override
    public <K> K[] getArray(Class<K> clazz)
    {
        ArrayList<K> list = getArrayList(clazz, getSqlFactory());
        return list.toArray((K[]) Array.newInstance(clazz, list.size()));
    }

    @Override
    public <K> Collection<K> getCollection(Class<K> clazz)
    {
        return getArrayList(clazz, getSqlFactory());
    }

    @Override
    public <K> K getObject(Class<K> clazz)
    {
        return getObject(clazz, getSqlFactory());
    }

    protected <K> K getObject(Class<K> clazz, FACTORY factory)
    {
        List<K> list = getArrayList(clazz, factory);

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
        forEach(block, getSqlFactory());
    }

    protected void forEach(final ForEachBlock<ResultSet> block, FACTORY factory)
    {
        factory.handleResultSet((new ResultSetHandler<Object>() {
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
        forEachMap(block, getSqlFactory());
    }

    protected void forEachMap(final ForEachBlock<Map<String, Object>> block, FACTORY factory)
    {
        factory.handleResultSet(new ResultSetHandler<Object>()
        {
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

    public interface ResultSetHandler<K>
    {
        K handle(ResultSet rs) throws SQLException;
    }


    // Wraps another SqlFactory, wrapping the SQL it produces with a SELECT COUNT(*) query.
    private class RowCountSqlFactory extends BaseSqlFactory
    {
        private final SqlFactory _factory;

        private RowCountSqlFactory(SqlFactory factory)
        {
            super(BaseSelector.this);
            _factory = factory;
        }

        @Override
        public SQLFragment getSql()
        {
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM (");
            sql.append(_factory.getSql());
            sql.append(") x");

            return sql;
        }
    }
}
