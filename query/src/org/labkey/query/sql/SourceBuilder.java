/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import com.google.errorprone.annotations.CompileTimeConstant;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;

import java.util.Collection;

/** Builds LabKey SQL source text; flagged so that getSQL() throws and any fragment it's appended to can't execute */
public class SourceBuilder extends Builder
{
    public SourceBuilder()
    {
        markNotExecutable();
    }

    @Override
    public SourceBuilder unsafeAppend(@CompileTimeConstant String str)
    {
        super.unsafeAppend(str);
        return this;
    }

    /** Appends parser token text verbatim, e.g. a string literal containing ';' */
    public SourceBuilder appendSourceText(CharSequence text)
    {
        if (text == null || text.isEmpty())
            return this;
        appendPrefix();
        appendIndent();
        appendUnchecked(text);
        return this;
    }

    public String getText()
    {
        return getRawSQL();
    }

    @Override
    public SQLFragment add(Object p)
    {
        throw noParameters();
    }

    @Override
    public SQLFragment add(Object p, JdbcType type)
    {
        throw noParameters();
    }

    @Override
    public SQLFragment addAll(Collection<?> l)
    {
        if (!l.isEmpty())
            throw noParameters();
        return this;
    }

    @Override
    public void prepend(SQLFragment sql)
    {
        if (!sql.getParamsNoCTEs().isEmpty())
            throw noParameters();
        super.prepend(sql);
    }

    private static IllegalStateException noParameters()
    {
        return new IllegalStateException("SourceBuilder holds LabKey SQL source text and can't take parameters");
    }
}
