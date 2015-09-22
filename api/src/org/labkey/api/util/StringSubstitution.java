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
package org.labkey.api.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;

/**
 * <code>StringSubstitution</code> runs regular expression string substitution
 * similar to Perl and Unix tools.  The regular expression is a standard Java
 * regular expression, and the substitution string uses the ${#} format to designate
 * matched "groups" from the regular expression.
 * <p>
 * e.g.    regex      = ([0-9.]+)@(\w)
 *         substitution = -n${2},${1}
 *         value      = 4.026655@K
 *         result     = -nK,4.026655 
 *
 * @author brendanx
 */
public class StringSubstitution
{
    private Pattern _pattern;
    private String _regex;
    private String _substitution;

    public StringSubstitution()
    {
    }

    public StringSubstitution(String regex, String substitution)
    {
        _regex = regex;
        _substitution = substitution;
    }

    public String getRegex()
    {
        return _regex;
    }

    public void setRegex(String regex)
    {
        _regex = regex;
        _pattern = Pattern.compile(regex);
    }

    public String getSubstitution()
    {
        return _substitution;
    }

    public void setSubstitution(String substitution)
    {
        String[] parts = substitution.split("\\$\\{");
        for (int i = 1; i < parts.length; i++)
        {
            if (parts[i].indexOf('}') == -1)
                throw new IllegalArgumentException("Invalid substitution string ending in group '" + parts[i] + "'");
            String[] subparts = parts[i].split("}");
            if (Integer.parseInt(subparts[0]) == 0 && !"0".equals(subparts[0]))
                throw new IllegalArgumentException("Invalid substitution group '" + subparts[0] + "'");
        }
        _substitution = substitution;
    }

    public String makeSubstitution(String value)
    {
        Matcher matcher = _pattern.matcher(value);
        if (matcher.matches())
        {
            MatchResult match = matcher.toMatchResult();

            StringBuilder valueSubst = new StringBuilder(value.substring(0, match.start()));
            String[] parts = _substitution.split("\\$\\{");
            valueSubst.append(parts[0]);
            for (int i = 1; i < parts.length; i++)
            {
                String[] subparts = parts[i].split("}");
                int group = Integer.parseInt(subparts[0]);
                if (group <= match.groupCount())
                    valueSubst.append(match.group(group));
                else
                    valueSubst.append("${").append(subparts[0]).append("}");
                for (int j = 1; j < subparts.length; j++)
                {
                    if (j > 1)
                        valueSubst.append("{");
                    valueSubst.append(subparts[j]);
                }
            }
            valueSubst.append(value.substring(match.end()));
            return valueSubst.toString();
        }

        return null;
    }
}
