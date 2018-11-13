/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.core.dialect;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ParameterMarkerInClauseGenerator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.PostgreSql91Dialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.core.admin.sql.ScriptReorderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * PostgreSQL 9.2 is no longer supported, however, we keep this class to track changes we implemented specifically for this version.
 *
 * User: adam
 * Date: 5/21/12
 * Time: 8:52 AM
 */
public abstract class PostgreSql92Dialect extends PostgreSql91Dialect
{
    protected PostgreSql92Dialect()
    {
    }

    protected PostgreSql92Dialect(boolean standardConformingStrings)
    {
        super(standardConformingStrings);
    }

    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.add("collation");

        return words;
    }

    /*
    These override method implementations were migrated from PostgreSql91Dialect when that class was promoted to api:
        getProductName()
        createStringHandler()
        getJdbcHelper()
        getScriptWarnings()
        initializeInClauseGenerator()
     */

    @Override
    public String getProductName()
    {
        return PostgreSqlDialectFactory.PRODUCT_NAME;
    }

    @Override
    protected DialectStringHandler createStringHandler()
    {
        // TODO: Isn't this the wrong setting?  Should we be looking at the "backslash_quote" setting instead?
        if (_standardConformingStrings)
            return super.createStringHandler();
        else
            return new PostgreSqlNonConformingStringHandler();
    }

    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper(PostgreSqlDialectFactory.JDBC_PREFIX);
    }

    @Override
    public Collection<String> getScriptWarnings(String name, String sql)
    {
        // Strip out all block- and single-line comments
        Pattern commentPattern = Pattern.compile(ScriptReorderer.COMMENT_REGEX, Pattern.DOTALL + Pattern.MULTILINE);
        Matcher matcher = commentPattern.matcher(sql);
        String noComments = matcher.replaceAll("");

        List<String> warnings = new LinkedList<>();

        // Split statements by semi-colon and CRLF
        for (String statement : noComments.split(";[\\n\\r]+"))
        {
            if (StringUtils.startsWithIgnoreCase(statement.trim(), "SET "))
                warnings.add(statement);
        }

        return warnings;
    }

    @Override
    protected void initializeInClauseGenerator(DbScope scope)
    {
        _inClauseGenerator = getJdbcVersion(scope) >= 4 ? new ArrayParameterInClauseGenerator(scope) : new ParameterMarkerInClauseGenerator();
    }

    /*
     TestCase migrated from PostgreSql91Dialect when that class promoted to api.
     */
    public static class TestCase extends Assert
    {
        PostgreSql92Dialect getDialect()
        {
            DbSchema core = CoreSchema.getInstance().getSchema();
            SqlDialect d = core.getSqlDialect();
            if (d instanceof PostgreSql92Dialect)
                return (PostgreSql92Dialect)d;
            return null;
        }

        @Test
        public void testInClause()
        {
            PostgreSql92Dialect d = getDialect();
            if (null == d)
                return;
            DbSchema core = CoreSchema.getInstance().getSchema();

            SQLFragment shortSql = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE userid ");
            d.appendInClauseSql(shortSql, Arrays.asList(1, 2, 3));
            assertEquals(1, new SqlSelector(core, shortSql).getRowCount());

            ArrayList<Object> l = new ArrayList<>();
            for (int i=1 ; i<=TEMPTABLE_GENERATOR_MINSIZE+1 ; i++)
                l.add(i);
            SQLFragment longSql = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE userid ");
            d.appendInClauseSql(longSql, l);
            assertEquals(1, new SqlSelector(core, longSql).getRowCount());

            SQLFragment shortSqlStr = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE displayname ");
            d.appendInClauseSql(shortSqlStr, Arrays.asList("1", "2", "3"));
            assertEquals(1, new SqlSelector(core, shortSqlStr).getRowCount());

            l = new ArrayList<>();
            for (int i=1 ; i<=TEMPTABLE_GENERATOR_MINSIZE+1 ; i++)
                l.add(String.valueOf(i));
            SQLFragment longSqlStr = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE displayname ");
            d.appendInClauseSql(longSqlStr, l);
            assertEquals(1, new SqlSelector(core, longSqlStr).getRowCount());
        }
    }
}
