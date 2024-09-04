package org.labkey.api.data;

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveCollection;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMap;
import org.labkey.api.util.ResultSetUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * An ObjectFactory that handles records. It doesn't care about the record's visibility (e.g., it can be private). All
 * maps are read in a case-insensitive manner.
 */
public class RecordFactory<K> implements ObjectFactory<K>
{
    private final Constructor<K> _constructor;
    private final Parameter[] _parameters;
    private final List<Field> _fields;

    public RecordFactory(Class<K> clazz)
    {
        assert clazz.isRecord() : clazz + " is not a record!";
        //noinspection unchecked
        _constructor = (Constructor<K>) clazz.getDeclaredConstructors()[0];
        _constructor.setAccessible(true);
        _parameters = _constructor.getParameters();
        _fields = Arrays.stream(clazz.getDeclaredFields())
            .peek(field -> field.setAccessible(true))
            .toList();
    }

    private <MAP extends Map<String, ?> & CaseInsensitiveCollection> K fromCaseInsensitiveMap(MAP m)
    {
        Object[] params = Arrays.stream(_parameters).map(p -> {
            Object value = m.get(p.getName());
            return ConvertUtils.convert(value, p.getType());
        }).toArray();

        try
        {
            return _constructor.newInstance(params);
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public K fromMap(Map<String, ?> m)
    {
        return fromCaseInsensitiveMap(CaseInsensitiveHashMap.ensure(m));
    }

    /**
     * Creates a new record from the map, same as above. Ignores the passed-in record since it's immutable.
     */
    @Override
    public K fromMap(K record, Map<String, ?> map)
    {
        return fromMap(map);
    }

    /**
     * Populates the passed-in map (if provided) or returns a CaseInsensitiveHashMap if map is null
     */
    @Override
    public Map<String, Object> toMap(K record, @Nullable Map<String, Object> m)
    {
        final Map<String, Object> map = (null == m ? new CaseInsensitiveHashMap<>() : m);

        _fields.forEach(field -> {
            try
            {
                map.put(field.getName(), field.get(record));
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
        });

        return map;
    }

    @Override
    public K handle(ResultSet rs) throws SQLException
    {
        Map<String, Object> map = ResultSetUtil.mapRow(rs);
        return fromMap(map);
    }

    @Override
    public ArrayList<K> handleArrayList(ResultSet rs) throws SQLException
    {
        Iterable<Map<String, Object>> iterable = () -> new ResultSetIterator(rs);
        return StreamSupport.stream(iterable.spliterator(), false)
            .map(rowMap -> fromCaseInsensitiveMap((RowMap<Object>)rowMap))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
