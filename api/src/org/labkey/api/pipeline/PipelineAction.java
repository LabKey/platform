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

/*
* User: jeckels
* Date: Jul 25, 2008
*/
public class PipelineAction
{
    public static final ParameterType COMMAND_LINE_PARAM = new ParameterType("Command line", "terms.labkey.org#CommandLine", SimpleTypeNames.STRING);

    private Set<URI> _inputs = new HashSet<URI>();
    private Set<URI> _outputs = new HashSet<URI>();
    private Set<URI> _tempOutputs = new HashSet<URI>();
    private Map<ParameterType, Object> _params = new HashMap<ParameterType, Object>();

    private String _name;

    public PipelineAction(TaskId taskId)
    {
        this(taskId.toString());
    }

    public PipelineAction(String name)
    {
        setName(name);
    }

    public void addInput(File input)
    {
        addInput(input.toURI());
    }

    public void addInput(URI input)
    {
        _inputs.add(input);
    }

    public void addOutput(File output, boolean tempFile)
    {
        addOutput(output.toURI(), tempFile);
    }

    public void addOutput(URI output, boolean tempFile)
    {
        _outputs.add(output);
        if (tempFile)
        {
            _tempOutputs.add(output);
        }
    }

    public Set<URI> getInputs()
    {
        return Collections.unmodifiableSet(_inputs);
    }

    public Set<URI> getOutputs()
    {
        return Collections.unmodifiableSet(_outputs);
    }

    public Set<URI> getTempOutputs()
    {
        return Collections.unmodifiableSet(_tempOutputs);
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void addParameter(ParameterType type, Object value)
    {
        _params.put(type, value);
    }

    public Map<ParameterType, Object> getParams()
    {
        return Collections.unmodifiableMap(_params);
    }

    public void addAll(WorkDirectory wd)
    {
        for (File file : wd.getInputs())
        {
            addInput(file);
        }

        for (File file : wd.getOutputs())
        {
            addOutput(file, false);
        }
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

        public String getUri()
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
}