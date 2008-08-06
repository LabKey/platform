/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.fhcrc.cpas.exp.xml.SimpleTypeNames;

import java.net.URI;
import java.util.*;
import java.io.File;

/**
 * Used to record an action performed by the pipeline. Consumed by XarGeneratorTask, which will create a full
 * experiment run to document the steps performed.
 * User: jeckels
 * Date: Jul 25, 2008
 */
public class RecordedAction
{
    public static final ParameterType COMMAND_LINE_PARAM = new ParameterType("Command line", "terms.labkey.org#CommandLine", SimpleTypeNames.STRING);

    private Set<DataFile> _inputs = new LinkedHashSet<DataFile>();
    private Set<DataFile> _outputs = new LinkedHashSet<DataFile>();
    private Map<ParameterType, Object> _params = new LinkedHashMap<ParameterType, Object>();

    private String _name;
    private String _description;

    public RecordedAction(String name)
    {
        setName(name);
        setDescription(name);
    }

    public void addInput(File input, String role)
    {
        addInput(input.toURI(), role);
    }

    public void addInput(URI input, String role)
    {
        _inputs.add(new DataFile(input, role, false));
    }

    public void addOutput(File output, String role, boolean transientFile)
    {
        addOutput(output.toURI(), role, transientFile);
    }

    public void addOutput(URI output, String role, boolean transientFile)
    {
        _outputs.add(new DataFile(output, role, transientFile));
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

    public void addParameter(ParameterType type, Object value)
    {
        _params.put(type, value);
    }

    public Map<ParameterType, Object> getParams()
    {
        return Collections.unmodifiableMap(_params);
    }

    public static class ParameterType
    {
        private String _uri;
        private String _name;
        private SimpleTypeNames.Enum _type;

        public ParameterType(String name, String uri, SimpleTypeNames.Enum type)
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

        public SimpleTypeNames.Enum getType()
        {
            return _type;
        }
    }

    public static class DataFile
    {
        private URI _uri;
        private String _role;
        private boolean _transient;

        public DataFile(URI uri, String role, boolean transientFile)
        {
            _uri = uri;
            _role = role;
            _transient = transientFile;
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
    }

    public String toString()
    {
        return _description + " Inputs: " + _inputs + " Outputs: " + _outputs;
    }
}