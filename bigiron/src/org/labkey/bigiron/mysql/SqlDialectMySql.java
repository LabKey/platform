package org.labkey.bigiron.mysql;

import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleSqlDialect;
import org.labkey.api.data.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: Aug 17, 2010
 * Time: 3:53:40 PM
 */
public class SqlDialectMySql extends SimpleSqlDialect
{
    @Override
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
    }

    @Override
    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
    }

    @Override
    protected String getProductName()
    {
        return "MySQL";
    }

    @Override
    protected boolean claimsProductNameAndVersion(String dataBaseProductName, int databaseMajorVersion, int databaseMinorVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException
    {
        if (!getProductName().equals(dataBaseProductName))
            return false;

        int version = databaseMajorVersion * 10 + databaseMinorVersion;   // 5.1 => 51, 5.5 => 55, etc.

        // Version 5.1 or greater is allowed...
        if (version >= 51)
        {
            // ...but warn for anything greater than 5.1
            if (logWarnings && version > 51)
                _log.warn("LabKey Server has not been tested against " + getProductName() + " version " + databaseMajorVersion + "." + databaseMinorVersion + ".  " +  getProductName() + " 5.1 is the recommended version.");

            return true;
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseMajorVersion + "." + databaseMinorVersion + " is not supported.  You must upgrade your database server installation to " + getProductName() + " version 5.1 or greater.");
    }

    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper("jdbc:mysql:");
    }

    @Override
    protected String getSIDQuery()
    {
        return "SELECT connection_id();";
    }

    @Override
    public boolean treatCatalogsAsSchemas()
    {
        return true;
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, DbScope scope)
    {
        return new MySqlColumnMetaDataReader(rsCols);
    }

    private static class MySqlColumnMetaDataReader extends ColumnMetaDataReader
    {
        private MySqlColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _sqlTypeNameKey = "TYPE_NAME";
            _scaleKey = "COLUMN_SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
        }

        @Override
        public boolean isAutoIncrement() throws SQLException
        {
            return getSqlTypeName().equalsIgnoreCase("int identity");
        }
    }


    @Override
    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }

    private SQLFragment limitRows(SQLFragment frag, int rowCount, long offset)
    {
        if (rowCount != Table.ALL_ROWS)
        {
            frag.append("\nLIMIT ");
            frag.append(Integer.toString(Table.NO_ROWS == rowCount ? 0 : rowCount));

            if (offset > 0)
            {
                frag.append(" OFFSET ");
                frag.append(Long.toString(offset));
            }
        }
        return frag;
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, int rowCount, long offset)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        SQLFragment sql = new SQLFragment();
        sql.append(select);
        sql.append("\n").append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (order != null) sql.append("\n").append(order);

        return limitRows(sql, rowCount, offset);
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int rowCount)
    {
        return limitRows(frag, rowCount, 0);
    }

    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    public String getCaseInsensitiveLikeOperator()
    {
        return "LIKE";
    }

    @Override
    public String getConcatenationOperator()
    {
        return "||";  // TODO: This is actually wrong... need to change this to concatenate(String... args) to implement CONCAT() properly
    }
}
