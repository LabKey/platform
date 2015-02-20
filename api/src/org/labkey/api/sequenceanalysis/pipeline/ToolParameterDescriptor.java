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

import com.drew.lang.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:28 PM
 */
public class ToolParameterDescriptor
{
    private CommandLineParam _ca;
    private String _name;
    private String _label;
    private String _description;
    private String _fieldXtype;
    private JSONObject _additionalExtConfig;
    private Object _defaultValue;

    public ToolParameterDescriptor(CommandLineParam ca, String name, String label, String description, String fieldXtype, @Nullable Object defaultValue, @Nullable JSONObject additionalExtConfig)
    {
        _ca = ca;
        _name = name;
        _label = label;
        _description = description;
        _fieldXtype = fieldXtype;
        _defaultValue = defaultValue;
        _additionalExtConfig = additionalExtConfig;
    }

    public static ToolParameterDescriptor create(String name, String label, String description, String fieldXtype, @Nullable JSONObject additionalExtConfig, @Nullable Object defaultValue)
    {
        return new ToolParameterDescriptor(null, name, label, description, fieldXtype, defaultValue, additionalExtConfig);
    }

    public static ToolParameterDescriptor createCommandLineParam(CommandLineParam ca, String name, String label, String description, String fieldXtype, @Nullable JSONObject additionalExtConfig, @Nullable Object defaultValue)
    {
        return new ToolParameterDescriptor(ca, name, label, description, fieldXtype, defaultValue, additionalExtConfig);
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public CommandLineParam getCommandLineParam()
    {
        return _ca;
    }

    public String getFieldXtype()
    {
        return _fieldXtype;
    }

    public JSONObject getAdditionalExtConfig()
    {
        return _additionalExtConfig;
    }

    public Object getDefaultValue()
    {
        return _defaultValue;
    }

    public JSONObject toJSON()
    {
        JSONObject ret = new JSONObject();
        ret.put("name", _name);
        ret.put("label", _label);
        ret.put("description", _description);
        ret.put("fieldXtype", _fieldXtype);
        ret.put("additionalExtConfig", _additionalExtConfig);
        ret.put("defaultValue", _defaultValue);
        ret.put("commandLineParam", _ca == null ? null : _ca.getArgName());


        return ret;
    }

    private String getJsonParamName(PipelineStepProvider provider)
    {
        PipelineStep.StepType type = PipelineStep.StepType.getStepType(provider.getStepClass());

        return type.name() + "." + provider.getName() + "." + getName();
    }

    public String extractValue(PipelineJob job, PipelineStepProvider provider) throws PipelineJobException
    {
        return extractValue(job, provider, String.class);
    }

    /**
     * Returns the value that will actually be added to the command line.  Can be overridden in subclasses
     * to perform last minute transforms, such as converted a boolean (which is better in the UI as a checkbox)
     * to 1 or 0
     */
    public String extractValueForCommandLine(PipelineJob job, PipelineStepProvider provider) throws PipelineJobException
    {
        return extractValue(job, provider);
    }

    public <ParamType> ParamType extractValue(PipelineJob job, PipelineStepProvider provider, Class<ParamType> clazz)
    {
        return this.extractValue(job, provider, clazz, null);
    }

    public <ParamType> ParamType extractValue(PipelineJob job, PipelineStepProvider provider, Class<ParamType> clazz, @Nullable ParamType defaultValue)
    {
        String key = getJsonParamName(provider);
        if (job.getParameters().containsKey(key))
        {
            String val = job.getParameters().get(key);

            return ConvertHelper.convert(val, clazz);
        }

        return defaultValue;
    }
}
