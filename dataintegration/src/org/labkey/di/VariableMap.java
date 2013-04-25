package org.labkey.di;

/**
* Created with IntelliJ IDEA.
* User: matthew
* Date: 4/22/13
* Time: 11:43 AM
*/
public interface VariableMap
{
    Object get(String key);
    Object put(String key, Object value);
    Object put(VariableDescription v, Object value);
}
