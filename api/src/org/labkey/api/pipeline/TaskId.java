/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.api.pipeline;

import java.io.Serializable;

/**
 * <code>TaskId</code>
 *
 * @author brendanx
 */
public class TaskId implements Serializable
{
    private Class _namespaceClass;
    private String _name;

    public TaskId(Class namespaceClass)
    {
        _namespaceClass = namespaceClass;
    }

    public TaskId(Class namespaceClass, String name)
    {
        _namespaceClass = namespaceClass;
        _name = name;
    }

    public TaskId(String s) throws ClassNotFoundException
    {
        String[] parts = s.split(":");
        _namespaceClass = Class.forName(parts[0]);
        _name = (parts.length > 1 ? parts[1] : null);        
    }

    public Class getNamespaceClass()
    {
        return _namespaceClass;
    }

    public String getName()
    {
        return _name;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaskId that = (TaskId) o;

        if (_name != null ? !_name.equals(that._name) : that._name != null) return false;
        if (!_namespaceClass.equals(that._namespaceClass)) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _namespaceClass.hashCode();
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        return result;
    }

    public String toString()
    {
        StringBuffer s = new StringBuffer(_namespaceClass.getName());
        if (_name != null)
            s.append(':').append(_name);
        return s.toString();
    }

    public static TaskId valueOf(String value) throws ClassNotFoundException
    {
        return new TaskId(value);
    }
}
