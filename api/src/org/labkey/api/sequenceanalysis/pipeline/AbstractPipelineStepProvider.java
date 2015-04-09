/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.view.template.ClientDependency;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 12:37 PM
 */
abstract public class AbstractPipelineStepProvider<StepType extends PipelineStep> implements PipelineStepProvider
{
    private String _name;
    private String _label;
    private String _toolName;
    private String _websiteURL;
    private String _description;
    private LinkedHashSet<String> _clientDependencyPaths;
    private List<ToolParameterDescriptor> _parameters;
    List<PipelineStepProvider> _prerequisites = new ArrayList<>();

    public AbstractPipelineStepProvider(String name, String label, @Nullable String toolName, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL)
    {
        _name = name;
        _label = label;
        _toolName = toolName;
        _description = description;
        _parameters = parameters == null ? Collections.<ToolParameterDescriptor>emptyList() : parameters;
        _clientDependencyPaths = clientDependencyPaths == null ? new LinkedHashSet<String>() : new LinkedHashSet<>(clientDependencyPaths);
        _websiteURL = websiteURL;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getLabel()
    {
        return _label;
    }

    @Override
    public String getToolName()
    {
        return _toolName;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public List<PipelineStepProvider> getPrerequisites()
    {
        return _prerequisites;
    }

    protected void addPrerequisites(PipelineStepProvider... providers)
    {
        for (PipelineStepProvider p : providers)
        {
            _prerequisites.add(p);
        }
    }

    @Override
    public Collection<ClientDependency> getClientDependencies()
    {
        //NOTE: because ClientDependency.fromPath() fails on remote servers, lazily parse the paths here
        LinkedHashSet<ClientDependency> clientDependencies = new LinkedHashSet<>();
        for (String path : _clientDependencyPaths)
        {
            clientDependencies.add(ClientDependency.fromFilePath(path));
        }

        return clientDependencies;
    }

    @Override
    public String getWebsiteURL()
    {
        return _websiteURL;
    }

    @Override
    public List<ToolParameterDescriptor> getParameters()
    {
        return Collections.unmodifiableList(_parameters);
    }

    @Override
    public ToolParameterDescriptor getParameterByName(String name)
    {
        if (_parameters != null)
        {
            for (ToolParameterDescriptor t : _parameters)
            {
                if (t.getName().equals(name))
                {
                    return t;
                }
            }
        }

        return null;
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("name", getName());
        json.put("label", getLabel());
        json.put("toolName", getToolName());
        json.put("description", getDescription());
        json.put("websiteURL", getWebsiteURL());
        JSONArray parameters = new JSONArray();
        for (ToolParameterDescriptor td : getParameters())
        {
            parameters.put(td.toJSON());
        }
        json.put("parameters", parameters);
        JSONArray prerequisites = new JSONArray();
        for (PipelineStepProvider p : getPrerequisites())
        {
            //prerequisites.put(td.toJSON());
        }
        json.put("prerequisites", prerequisites);
        return json;
    }

    @Override
    public Class<StepType> getStepClass()
    {
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        return (Class) parameterizedType.getActualTypeArguments()[0];
    }
}
