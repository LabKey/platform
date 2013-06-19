/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.query.sql;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.SQLFragment;

import java.util.List;
import java.util.ArrayList;

public class Builder extends SQLFragment
{
    int _cIndent;
    boolean _fNewLine;
    List<String> _prefix;

    public Builder()
    {
        _cIndent = 0;
        _fNewLine = true;
        _prefix = new ArrayList<>();
    }

    /**
     * Set the string that needs to be output before the
     * next bit of text is output.
     * (for instance, to say, "before the next expression is output,
     * we need to output "FROM", unless there are no more expressions.)
     * @param prefix
     */
    public void pushPrefix(String prefix)
    {
        _prefix.add(prefix);
    }

    /**
     *
     * @param suffix: thing that should be appended if the prefix has
     * been written out.
     */
    public void popPrefix(String suffix)
    {
        String cur = _prefix.remove(_prefix.size() - 1);
        if (cur == null)
        {
            super.append(suffix);
        }
    }

    public void popPrefix()
    {
        popPrefix("");
    }

    /**
     * If the current prefix has already been output, then change it
     * to "newPrefix".  Otherwise, leave it as it was.
     */
    public boolean nextPrefix(String newPrefix)
    {
        String prefix = _prefix.get(_prefix.size() - 1);
        if (prefix != null)
            return false;
        _prefix.set(_prefix.size() - 1, newPrefix);
        return true;
    }

    private void appendPrefix()
    {
        for (int i = 0; i < _prefix.size(); i ++)
        {
            String prefix = _prefix.get(i);
            if (prefix == null)
                continue;
            super.append(prefix);
            _prefix.set(i, null);
        }
    }

    private void appendIndent()
    {
        if (_fNewLine)
        {
            super.append(StringUtils.repeat(" ", _cIndent));
            _fNewLine = false;
        }
    }

    public Builder append(CharSequence cs)
    {
        if (cs == null || cs.length() == 0)
            return this;
        appendPrefix();
        appendIndent();
        super.append(cs);
        return this;
    }

    public void newLine()
    {
        super.append("\n");
        _fNewLine = true;
    }
}
