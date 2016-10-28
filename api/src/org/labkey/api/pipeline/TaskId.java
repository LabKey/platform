/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
 * Identifier for a pipeline definition. Used for both top-level pipelines, as well as tasks within a pipeline.
 *
 * <dl>
 *     <dt>namespaceClass</dt>
 *     <dt>Java Class of the task</dt>
 *
 *     <dt>namespaceClass:name</dt>
 *     <dd>Java Class of the task with specific name</dd>
 *
 *     <dt>module:type:name[:version]?</dt>
 *     <dd>Declaring module name, task type (task or job), friendly name, and optional version id.</dd>
 * </dl>
 *
 * @author brendanx
 */
public class TaskId implements Serializable
{
    public enum Type { task, pipeline }

    final private String _moduleName;
    final private Type _type;
    final private Class _namespaceClass;
    final private String _name;
    final private double _version;

    public TaskId(Class namespaceClass)
    {
        _moduleName = null;
        _type = null;
        _namespaceClass = namespaceClass;
        _name = null;
        _version = 0;
    }

    public TaskId(Class namespaceClass, String name)
    {
        _moduleName = null;
        _type = null;
        _namespaceClass = namespaceClass;
        _name = name;
        _version = 0;
    }

    public TaskId(String moduleName, Type type, String name, double version)
    {
        _moduleName = moduleName;
        _type = type;
        _namespaceClass = null;
        _name = name;
        _version = version;
    }

    public TaskId(String s) throws ClassNotFoundException
    {
        String[] parts = s.split(":");

        if (parts.length == 1)
        {
            // Parse as "namespaceClass"
            _moduleName = null;
            _type = null;
            _namespaceClass = Class.forName(parts[0]);
            _name = null;
            _version = 0;
        }
        else if (parts.length == 2)
        {
            // Parse as "namespaceClass:name"
            _moduleName = null;
            _type = null;
            _namespaceClass = Class.forName(parts[0]);
            _name = decode(parts[1]);
            _version = 0;
        }
        else if (parts.length == 3 || parts.length == 4)
        {
            // Parse as either "module:type:name" or "module:type:name:version"
            _moduleName = decode(parts[0]);
            _type = Type.valueOf(decode(parts[1]));
            _namespaceClass = null;
            _name = decode(parts[2]);
            _version = parts.length == 4 ? Double.valueOf(parts[3]) : 0;
        }
        else
            throw new IllegalArgumentException("unsupported taskid format");
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public Type getType()
    {
        return _type;
    }

    public Class getNamespaceClass()
    {
        return _namespaceClass;
    }

    public String getName()
    {
        return _name;
    }

    public double getVersion()
    {
        return _version;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaskId taskId = (TaskId) o;

        if (Double.compare(taskId._version, _version) != 0) return false;
        if (_moduleName != null ? !_moduleName.equals(taskId._moduleName) : taskId._moduleName != null) return false;
        if (_name != null ? !_name.equals(taskId._name) : taskId._name != null) return false;
        if (_namespaceClass != null ? !_namespaceClass.equals(taskId._namespaceClass) : taskId._namespaceClass != null)
            return false;
        if (_type != taskId._type) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result;
        long temp;
        result = _moduleName != null ? _moduleName.hashCode() : 0;
        result = 31 * result + (_type != null ? _type.hashCode() : 0);
        result = 31 * result + (_namespaceClass != null ? _namespaceClass.hashCode() : 0);
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        temp = Double.doubleToLongBits(_version);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    /**
     * Generate canonical task/pipeline name parsable by {@link #valueOf(String)}.
     */
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        if (_moduleName != null && _type != null && _name != null)
        {
            s.append(encode(_moduleName));
            s.append(":");
            s.append(encode(_type.name()));
            s.append(":");
            s.append(encode(_name));
            if (_version > 0)
            {
                s.append(":");
                s.append(_version);
            }
        }
        else
        {
            // Classname can't contain ':' so no encoding is needed.
            s.append(_namespaceClass.getName());
            if (_name != null)
                s.append(':').append(encode(_name));
        }
        return s.toString();
    }

    public static TaskId valueOf(String value) throws ClassNotFoundException
    {
        return new TaskId(value);
    }

    // TODO: Will replace with PageFlowUtil.encodeURIComponent(), but DataItegration currently
    // TODO: generates task ids of the form: org.labkey.di.pipeline.TransformPipelineJob:{DataIntegration}/unit
    // TODO: which will be encoded but then won't match the expected ExpRun protocol name of "{DataIntegration}/unit"
    // TODO: Fix TransformManager.get().createConfigId() to use TaskId or encode or something
    // TODO: and make sure it's backwards compatible with existing ids that may be stored in the database.
    private static String encode(String s)
    {
        return s.replaceAll(":", "%3A");
    }

    private static String decode(String s)
    {
        return s.replaceAll("%3A", ":");
    }
}
