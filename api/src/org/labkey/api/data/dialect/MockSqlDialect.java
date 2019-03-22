/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.data.dialect;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Set;

/**
 * User: adam
 * Date: 8/29/12
 * Time: 4:02 PM
 */
public class MockSqlDialect extends SimpleSqlDialect
{
    @Override
    public String getProductName()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String concatenate(String... args)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public SQLFragment concatenate(SQLFragment... args)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public JdbcHelper getJdbcHelper()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        return Collections.emptySet();
    }

    @Override
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }
}
