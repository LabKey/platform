package org.labkey.api.data;

import org.labkey.api.query.FieldKey;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* User: kevink
* Date: 3/5/14
*/
class FieldKeyRowMap implements Map<FieldKey, Object>
{
    private Results _results;

    public FieldKeyRowMap(Results results)
    {
        _results = results;
    }

    @Override
    public int size()
    {
        return _results.getFieldIndexMap().size();
    }

    @Override
    public boolean isEmpty()
    {
        return _results.getFieldIndexMap().isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return _results.getFieldIndexMap().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get(Object key)
    {
        try
        {
            return _results.getObject((FieldKey) key);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object put(FieldKey key, Object value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object remove(Object key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends FieldKey, ? extends Object> m)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<FieldKey> keySet()
    {
        return _results.getFieldIndexMap().keySet();
    }

    @Override
    public Collection<Object> values()
    {
        List<Object> list = new LinkedList<>();

        for (FieldKey key : _results.getFieldIndexMap().keySet())
            list.add(get(key));

        return list;
    }

    @Override
    public Set<Map.Entry<FieldKey, Object>> entrySet()
    {
        HashSet<Map.Entry<FieldKey, Object>> map = new HashSet<>(_results.getFieldIndexMap().size());

        for (FieldKey key : _results.getFieldIndexMap().keySet())
            map.add(new Entry(key));

        return map;
    }

    private class Entry implements Map.Entry<FieldKey, Object>
    {
        private final FieldKey _key;

        private Entry(FieldKey key)
        {
            _key = key;
        }

        public FieldKey getKey()
        {
            return _key;
        }

        public Object getValue()
        {
            return get(_key);
        }

        public Object setValue(Object v)
        {
            throw new UnsupportedOperationException();
        }
    }
}
