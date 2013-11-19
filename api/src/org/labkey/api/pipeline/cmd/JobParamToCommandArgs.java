/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipelineJob;

import java.util.Map;

/**
 * <code>JobParamToCommandArgs</code>
*/
abstract public class JobParamToCommandArgs extends TaskToCommandArgs
{
    private String _parameter;
    private ParamParser.ParamValidator _paramValidator;
    private String _help;
    private String _default;

    public String getParameter()
    {
        return _parameter;
    }

    public void setParameter(String parameter)
    {
        _parameter = parameter;
    }

    public ParamParser.ParamValidator getParamValidator()
    {
        return _paramValidator;
    }

    public void setParamValidator(ParamParser.ParamValidator paramValidator)
    {
        _paramValidator = paramValidator;
    }

    public String getDefault()
    {
        return _default;
    }

    public void setDefault(String value)
    {
        _default = value;
    }

    public String getHelp()
    {
        return _help;
    }

    public void setHelp(String help)
    {
        _help = help;
    }

    @Nullable
    public String getValue(@NotNull PipelineJob job)
    {
        Map<String, String> jobParams = job.getParameters();
        return getValue(jobParams);
    }

    @Nullable
    public String getValue(@Nullable Map<String, String> jobParams)
    {
        String value = jobParams == null ? null : jobParams.get(getParameter());
        return value == null ? getDefault() : value;
    }

}
