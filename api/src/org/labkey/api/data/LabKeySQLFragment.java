/*
 * Copyright (c) 2026 LabKey Corporation
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

package org.labkey.api.data;

import com.google.errorprone.annotations.CompileTimeConstant;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.sql.LabKeySql;

import java.util.Collection;
import java.util.List;

/// LabKey SQL source text, e.g. a query handed to `QueryService` as a String. Marked not executable, so `getSQL()` throws
/// and any fragment it's appended to can't run against the database; read the text with [#getText()].
/// Parameters are rejected because the text leaves as a String; inline values with [#appendValue(CharSequence)].
/// Mirrors query's `SourceBuilder`, which extends `Builder` for prefix/indent handling and so can't share this class.
public class LabKeySQLFragment extends SQLFragment
{
    public LabKeySQLFragment()
    {
        markNotExecutable();
    }

    public static LabKeySQLFragment unsafe(@CompileTimeConstant String text)
    {
        return new LabKeySQLFragment().unsafeAppend(text);
    }

    public static LabKeySQLFragment ofSourceText(CharSequence text)
    {
        return new LabKeySQLFragment().appendSourceText(text);
    }

    @Override
    public LabKeySQLFragment unsafeAppend(@CompileTimeConstant String str)
    {
        super.unsafeAppend(str);
        return this;
    }

    /** Appends LabKey SQL text verbatim, e.g. a select list or a schema-qualified query name */
    public LabKeySQLFragment appendSourceText(CharSequence text)
    {
        if (text == null || text.isEmpty())
            return this;
        appendUnchecked(text);
        return this;
    }

    /** Appends a LabKey SQL string literal, doubling embedded quotes */
    public LabKeySQLFragment appendStringLiteral(CharSequence s)
    {
        if (null == s)
            return appendSourceText("NULL");
        appendUnchecked(LabKeySql.quoteString(s.toString()));
        return this;
    }

    /** Inlines the value as a LabKey SQL string literal; SQLFragment.appendValue(CharSequence) would add a parameter */
    @Override
    public LabKeySQLFragment appendValue(CharSequence s)
    {
        return appendStringLiteral(s);
    }

    @Override
    public LabKeySQLFragment append(SQLFragment f)
    {
        super.append(f);
        return this;
    }

    public String getText()
    {
        return getRawSQL();
    }

    /** Throws if a parameter got in by a path that skips add(), e.g. a CTE merged by append(SQLFragment) */
    @Override
    public String getRawSQL()
    {
        if (!getParams().isEmpty())
            throw noParameters();
        return super.getRawSQL();
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
        return new IllegalStateException("LabKeySQLFragment holds LabKey SQL source text and can't take parameters");
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testText()
        {
            LabKeySQLFragment sql = LabKeySQLFragment.unsafe("SELECT Name FROM ")
                    .appendSourceText("exp.materials")
                    .unsafeAppend(" WHERE Name = ")
                    .appendStringLiteral("it's");
            assertEquals("SELECT Name FROM exp.materials WHERE Name = 'it''s'", sql.getText());
            assertEquals("NULL", new LabKeySQLFragment().appendStringLiteral(null).getText());
            assertEquals("'x' = 'it''s'", new LabKeySQLFragment().appendValue("x").unsafeAppend(" = ").appendValue("it's").getText());
            assertEquals("NULL", new LabKeySQLFragment().appendValue((CharSequence) null).getText());
            assertEquals("NOT (a)", LabKeySQLFragment.unsafe("NOT (").append(LabKeySQLFragment.ofSourceText("a")).unsafeAppend(")").getText());
        }

        @Test
        public void testNotExecutable()
        {
            LabKeySQLFragment sql = LabKeySQLFragment.unsafe("SELECT 1");
            assertThrows(IllegalStateException.class, sql::getSQL);
            assertThrows(IllegalStateException.class, () -> new SQLFragment().append(sql).getSQL());
        }

        @Test
        public void testNoParameters()
        {
            assertThrows(IllegalStateException.class, () -> new LabKeySQLFragment().add(1));
            assertThrows(IllegalStateException.class, () -> new LabKeySQLFragment().append(SQLFragment.unsafe("x = ?", 1)));
            assertTrue(new LabKeySQLFragment().addAll(List.of()).getParams().isEmpty());

            LabKeySQLFragment withCte = LabKeySQLFragment.unsafe("SELECT 1");
            withCte.addCommonTableExpression(CoreSchema.getInstance().getSqlDialect(), new Object(), "cte", SQLFragment.unsafe("SELECT ?", 1));
            assertThrows(IllegalStateException.class, withCte::getText);
        }
    }
}
