package org.labkey.di.pipeline;

import org.labkey.api.collections.CaseInsensitiveHashMap;


/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 4/22/13
 * Time: 11:44 AM
 */
public class VariableMapImpl implements VariableMap
{
    final VariableMap _outer;
    final CaseInsensitiveHashMap<VariableDescription> declarations = new CaseInsensitiveHashMap<>();
    final CaseInsensitiveHashMap<Object> values = new CaseInsensitiveHashMap<>();

    public VariableMapImpl(VariableMap parentScope)
    {
        _outer = parentScope;
    }


    @Override
    public Object get(String key)
    {
        if (null == _outer || values.containsKey(key))
            return values.get(key);
        return _outer.get(key);
    }


    @Override
    public Object put(String key, Object value)
    {
        VariableDescription d = declarations.get(key);
        if (null != d)
            value = d.getJdbcType().convert(value);
        return values.put(key,value);
    }


    public Object put(VariableDescription var, Object value)
    {
        declarations.put(var.getName(), var);
        return put(var.getName(), value);
    }
}
