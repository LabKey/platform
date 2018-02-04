/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Used to record an action performed by the pipeline. Consumed by XarGeneratorTask, which will create a full
 * experiment run to document the steps performed.
 * User: jeckels
 * Date: Jul 25, 2008
 */
public class RecordedAction
{
    public static final ParameterType COMMAND_LINE_PARAM = new ParameterType("Command line", "terms.labkey.org#CommandLine", PropertyType.STRING);

    private Set<DataFile> _inputs = new LinkedHashSet<>();
    private Set<DataFile> _outputs = new LinkedHashSet<>();
    private Map<ParameterType, Object> _params = new LinkedHashMap<>();
    private Map<ParameterType, Object> _outputParams = new LinkedHashMap<>();
    private Map<PropertyDescriptor, Object> _props = new LinkedHashMap<>();
    private String _name;
    private String _description;
    private Date _startTime;
    private Date _endTime;
    private Integer _recordCount;

    /** No-args constructor to support de-serialization in Java 7 and beyond */
    @SuppressWarnings({"UnusedDeclaration"})
    public RecordedAction() {}

    public RecordedAction(String name)
    {
        setName(name);
        setDescription(name);
    }

    public void addInput(File input, String role)
    {
        addInput(input.toURI(), role);
    }

    private boolean uriExists(URI toTest, Set<DataFile> set)
    {
        for (DataFile df : set)
        {
            if (toTest.equals(df.getURI()))
            {
                return true;
            }
        }

        return false;
    }

    public void addInput(URI input, String role)
    {
        addInput(input, role, true);
    }

    public void addInputIfNotPresent(File input, String role)
    {
        addInput(input.toURI(), role, false);
    }

    /**
     * Exp.data has a constraint that will only allow a given file
     * once per action, so by default this will throw an exception
     * if the same file is added twice as an input.  Alternately,
     * addInputIfNotPresent() which will silently ignore duplicate files.
     */
    private void addInput(URI input, String role, boolean throwIfExists)
    {
        if (!uriExists(input, _inputs))
        {
            _inputs.add(new DataFile(input, role, false, false));
        }
        else if (throwIfExists)
        {
            throw new IllegalArgumentException("Already has been added as an input for the action " + getName() + ":" + FileUtil.uriToString(input));
        }
    }

    /**
     * Exp.data has a constraint that will only allow a given file
     * once per action, so by default this will throw an exception
     * if the same file is added twice as an output.  Alternately,
     * addOutputIfNotPresent() which will silently ignore duplicate files.
     */
    public void addOutput(File output, String role, boolean transientFile)
    {
        addOutput(output.toURI(), role, transientFile, false);
    }

    public void addOutputIfNotPresent(File output, String role, boolean transientFile)
    {
        addOutput(output.toURI(), role, transientFile, false, false);
    }

    public void addOutput(File output, String role, boolean transientFile, boolean generated)
    {
        addOutput(output.toURI(), role, transientFile, generated);
    }

    public void addOutput(URI output, String role, boolean transientFile)
    {
        addOutput(output, role, transientFile, false);
    }

    public void addOutput(URI output, String role, boolean transientFile, boolean generated)
    {
        addOutput(output, role, transientFile, generated, true);
    }

    private void addOutput(URI output, String role, boolean transientFile, boolean generated, boolean throwIfExists)
    {
        if (!uriExists(output, _outputs))
        {
            _outputs.add(new DataFile(output, role, transientFile, generated));
        }
        else if (throwIfExists)
        {
            throw new IllegalArgumentException("Already has been added as an output for the action " + getName() + ":" + FileUtil.uriToString(output));
        }
    }

    public Set<DataFile> getInputs()
    {
        return Collections.unmodifiableSet(_inputs);
    }

    public Set<DataFile> getOutputs()
    {
        return Collections.unmodifiableSet(_outputs);
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public void setStartTime(Date startTime)
    {
        _startTime = startTime;
    }

    public Date getStartTime()
    {
        return _startTime;
    }

    public void setEndTime(Date endTime)
    {
        _endTime = endTime;
    }

    public Date getEndTime()
    {
        return _endTime;
    }

    public void setRecordCount(Integer recordCount)
    {
        _recordCount = recordCount;
    }

    public Integer getRecordCount()
    {
        return _recordCount;
    }

    public void addParameter(ParameterType type, Object value)
    {
        _params.put(type, value);
    }

    public void addOutputParameter(ParameterType type, Object value)
    {
        _outputParams.put(type, value);
    }

    public void addProperty(PropertyDescriptor pd, Object value )
    {
        _props.put(pd,value);
    }

    public Map<ParameterType, Object> getParams()
    {
        return Collections.unmodifiableMap(_params);
    }

    public Map<ParameterType, Object> getOutputParams()
    {
        return Collections.unmodifiableMap(_outputParams);
    }

    public Map<PropertyDescriptor, Object> getProps()
    {
        return Collections.unmodifiableMap(_props);
    }

    public static class ParameterType
    {
        public static String createUri(String name)
        {
            return "terms.labkey.org#" + name.replaceAll("\\s","");
        }

        private String _uri;
        private String _name;
        private PropertyType _type;

        // No-args constructor to support de-serialization in Java 7
        @SuppressWarnings({"UnusedDeclaration"})
        public ParameterType()
        {
        }

        public ParameterType(String name, PropertyType type)
        {
            this(name, createUri(name), type);
        }

        public ParameterType(String name, String uri, PropertyType type)
        {
            _name = name;
            _uri = uri;
            _type = type;
        }

        public String getURI()
        {
            return _uri;
        }

        public String getName()
        {
            return _name;
        }

        public PropertyType getType()
        {
            return _type;
        }
    }

    public static class DataFile
    {
        private URI _uri;
        private String _role;
        private boolean _transient;
        private boolean _generated;

        // No-args constructor to support de-serialization in Java 7
        @SuppressWarnings({"UnusedDeclaration"})
        public DataFile()
        {
        }

        public DataFile(URI uri, String role, boolean transientFile, boolean generated)
        {
            _uri = uri;
            _role = role;
            _transient = transientFile;
            _generated = generated;
        }

        public URI getURI()
        {
            return _uri;
        }

        public String getRole()
        {
            return _role;
        }

        public boolean isTransient()
        {
            return _transient;
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DataFile that = (DataFile) o;

            if (_role != null ? !_role.equals(that._role) : that._role != null) return false;
            return !(_uri != null ? !_uri.equals(that._uri) : that._uri != null);
        }

        public int hashCode()
        {
            int result;
            result = (_uri != null ? _uri.hashCode() : 0);
            result = 31 * result + (_role != null ? _role.hashCode() : 0);
            return result;
        }

        public boolean isGenerated()
        {
            return _generated;
        }
    }

    public String toString()
    {
        return _description + " Inputs: " + _inputs + " Outputs: " + _outputs;
    }

    public boolean updateForMovedFile(File original, File moved)
    {
        boolean changed = false;

        if (potentiallySwapFiles(original, moved, _inputs))
        {
            changed = true;
        }

        if (potentiallySwapFiles(original, moved, _outputs))
        {
            changed = true;
        }

        return changed;
    }

    private boolean potentiallySwapFiles(File original, File moved, Set<DataFile> toInspect)
    {
        boolean changed = false;
        for (DataFile df : toInspect)
        {
            if (original.toURI().equals(df.getURI()))
            {
                df._uri = moved.toURI();
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RecordedAction that = (RecordedAction) o;

        if (_description != null ? !_description.equals(that._description) : that._description != null) return false;
        if (_inputs != null ? !_inputs.equals(that._inputs) : that._inputs != null) return false;
        if (_name != null ? !_name.equals(that._name) : that._name != null) return false;
        if (_outputs != null ? !_outputs.equals(that._outputs) : that._outputs != null) return false;
        return !(_params != null ? !_params.equals(that._params) : that._params != null);
    }

    @Override
    public int hashCode()
    {
        int result = _inputs != null ? _inputs.hashCode() : 0;
        result = 31 * result + (_outputs != null ? _outputs.hashCode() : 0);
        result = 31 * result + (_params != null ? _params.hashCode() : 0);
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        result = 31 * result + (_description != null ? _description.hashCode() : 0);
        return result;
    }
}
