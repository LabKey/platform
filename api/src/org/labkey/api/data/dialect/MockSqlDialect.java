package org.labkey.api.data.dialect;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;

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
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, DbSchema schema)
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
}
