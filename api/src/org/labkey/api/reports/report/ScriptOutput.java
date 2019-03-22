/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

package org.labkey.api.reports.report;

/**
 * Represents an output from script execution.  This output
 * could be an output parameter, a runtime error, or console
 * output
 */
public class ScriptOutput
{
    public enum ScriptOutputType
    {
        console,
        error,
        text,
        html,
        svg,
        tsv,
        image,
        pdf,
        file,
        postscript,
        json
    };

    public ScriptOutputType _type;
    public String _name;
    public String _value;

    public ScriptOutput(ScriptOutputType type, String name, String value)
    {
        _type = type;
        _name = name;
        _value = value;
    }

    public ScriptOutputType getType()
    {
        return _type;
    }

    public String getName()
    {
        return _name;
    }

    public String getValue()
    {
        return _value;
    }
}
