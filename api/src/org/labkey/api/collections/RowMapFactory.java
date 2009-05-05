package org.labkey.api.collections;

import java.util.Map;
import java.util.List;
import java.util.Arrays;

/**
 * User: adam
 * Date: Apr 30, 2009
 * Time: 11:30:38 PM
 */
public class RowMapFactory<V>
{
    private final Map<String, Integer> _findMap;

    public RowMapFactory()
    {
        _findMap = new CaseInsensitiveHashMap<Integer>();
    }

    public RowMapFactory(int columns)
    {
        _findMap = new CaseInsensitiveHashMap<Integer>(2 * columns);
    }

    public RowMapFactory(Map<String, Integer> findMap)
    {
        _findMap = findMap;
    }

    protected RowMap<V> getRowMap()
    {
        return new RowMap<V>(_findMap);
    }

    public RowMap<V> getRowMap(List<V> row)
    {
        return new RowMap<V>(_findMap, row);
    }

    public RowMap<V> getRowMap(V[] row)
    {
        return new RowMap<V>(_findMap, Arrays.asList(row));      // TODO: Pass through actual array?  Different class?  Static factory?
    }

    protected Map<String, Integer> getFindMap()
    {
        return _findMap;
    }
}
