/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.labkey.api.util.StringSubstitution;

import java.util.Collections;
import java.util.List;

/**
 * <code>SubstitutionInLine</code>
*/
public class SubstitutionInLine extends ValueToCommandArgs
{
    private StringSubstitution _converter = new StringSubstitution();

    public String getRegex()
    {
        return _converter.getRegex();
    }

    public void setRegex(String regex)
    {
        _converter.setRegex(regex);
    }

    public String getSubstitution()
    {
        return _converter.getSubstitution();
    }

    public void setSubstitution(String substitution)
    {
        _converter.setSubstitution(substitution);
    }

    public List<String> toArgs(String value)
    {
        if (value != null)
        {
            String valueSubst = _converter.makeSubstitution(value);
            if (valueSubst != null)
                return Collections.singletonList(valueSubst);
        }

        return Collections.emptyList();
    }
}
