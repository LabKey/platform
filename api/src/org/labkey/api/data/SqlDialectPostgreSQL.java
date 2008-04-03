/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.query.AliasManager;

import javax.servlet.ServletException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Dialect specifics for PostgreSQL
public class SqlDialectPostgreSQL extends SqlDialect
{
    private static SqlDialectPostgreSQL _instance = new SqlDialectPostgreSQL();

    public static SqlDialectPostgreSQL getInstance()
    {
        return _instance;
    }

    public SqlDialectPostgreSQL()
    {
        super();
        reservedWordSet = new CaseInsensitiveHashSet(PageFlowUtil.set(
                "ALL", "ANALYSE", "ANALYZE", "AND", "ANY", "ARRAY", "AS", "ASC", "ASYMMETRIC",
                "AUTHORIZATION", "BETWEEN", "BINARY", "BOTH", "CASE", "CAST", "CHECK", "COLLATE", "COLUMN", "CONSTRAINT",
                "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_ROLE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "DEFAULT", "DEFERRABLE", "DESC",
                "DISTINCT", "DO", "ELSE", "END", "EXCEPT", "FALSE", "FOR", "FOREIGN", "FREEZE",
                "FROM", "FULL", "GRANT", "GROUP", "HAVING", "ILIKE", "IN", "INITIALLY", "INNER",
                "INTERSECT", "INTO", "IS", "ISNULL", "JOIN", "LEADING", "LEFT", "LIKE", "LIMIT",
                "LOCALTIME", "LOCALTIMESTAMP", "NATURAL", "NEW", "NOT", "NOTNULL", "NULL", "OFF", "OFFSET",
                "OLD", "ON", "ONLY", "OR", "ORDER", "OUTER", "OVERLAPS", "PLACING", "PRIMARY",
                "REFERENCES", "RIGHT", "SELECT", "SESSION_USER", "SIMILAR", "SOME", "SYMMETRIC", "TABLE", "THEN",
                "TO", "TRAILING", "TRUE", "UNION", "UNIQUE", "USER", "USING", "VERBOSE", "WHEN", "WHERE"
        ));
    }

    public String getProductName()
    {
        return "PostgreSQL";
    }

    public String getSQLScriptPath()
    {
        return "postgresql";
    }

    public String getDefaultDateTimeDatatype()
    {
        return "TIMESTAMP";
    }

    public String getUniqueIdentType()
    {
        return "SERIAL";
    }

    @Override
    public void appendStatement(StringBuilder sql, String statement)
    {
        sql.append(";\n");
        sql.append(statement);
    }


    @Override
    public void appendSelectAutoIncrement(StringBuilder sql, TableInfo table, String columnName)
    {
        if (null == table.getSequence())
            appendStatement(sql, "SELECT CURRVAL('" + table.toString() + "_" + columnName + "_seq')");
        else
            appendStatement(sql, "SELECT CURRVAL('" + table.getSequence() + "')");
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int rowCount)
    {
        return limitRows(frag, rowCount, 0);
    }

    private SQLFragment limitRows(SQLFragment frag, int rowCount, long offset)
    {
        if (rowCount > 0)
        {
            frag.append("\nLIMIT ");
            frag.append(Integer.toString(rowCount));
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
    public boolean supportOffset()
    {
        return true;
    }

    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "SELECT " + schema.getOwner() + "." + procedureName + "(" + parameters + ")";
    }


    public String getConcatenationOperator()
    {
        return "||";
    }


    public String getCharClassLikeOperator()
    {
        return "SIMILAR TO";
    }

    public String getCaseInsensitiveLikeOperator()
    {
        return "ILIKE";
    }

    public String getVarcharLengthFunction()
    {
        return "length";
    }

    public String getStdDevFunction()
    {
        return "stddev";
    }

    public String getClobLengthFunction()
    {
        return "length";
    }

    public String getStringIndexOfFunction(String stringToFind, String stringToSearch)
    {
        return "position(" + stringToFind + " in " + stringToSearch + ")";
    }

    public String getSubstringFunction(String s, String start, String length)
    {
        return "substr(" + s + ", " + start + ", " + length + ")"; 
    }

    protected String getSystemTableNames()
    {
        return "pg_logdir_ls";
    }

    public String sanitizeException(SQLException ex)
    {
        if ("22001".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    public String getAnalyzeCommandForTable(String tableName)
    {
        return "ANALYZE " + tableName;
    }

    protected String getSIDQuery()
    {
        return "SELECT pg_backend_pid();";
    }

    public String getBooleanDatatype()
    {
        return "BOOLEAN";
    }

    public String getTempTableKeyword()
    {
        return "TEMPORARY";
    }

    public String getTempTablePrefix()
    {
        return "";
    }

    public String getGlobalTempTablePrefix()
    {
        return "temp.";
    }

    public boolean isNoDatabaseException(SQLException e)
    {
        return "3D000".equals(e.getSQLState());
    }

    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return true;
    }

    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + indexName;
    }

    public String getCreateDatabaseSql(String dbName)
    {
        return "CREATE DATABASE \"" + dbName + "\" WITH ENCODING 'UTF8';\n" +
                "ALTER DATABASE \"" + dbName + "\" SET default_with_oids TO OFF";
    }

    public String getCreateSchemaSql(String schemaName)
    {
        if (!AliasManager.isLegalName(schemaName) || reservedWordSet.contains(schemaName))
            throw new IllegalArgumentException("Not a legal schema name: " + schemaName);
        //Quoted schema names are bad news
        return "CREATE SCHEMA " + schemaName;
    }

    public String getDateDiff(int part, String value1, String value2)
    {
        int divideBy;
        switch (part)
        {
            case Calendar.DATE:
            {
                divideBy = 60 * 60 * 24;
                break;
            }
            case Calendar.HOUR:
            {
                divideBy = 60 * 60;
                break;
            }
            case Calendar.MINUTE:
            {
                divideBy = 60;
                break;
            }
            case Calendar.SECOND:
            {
                divideBy = 1;
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unsupported time unit: " + part);
            }
        }
        return "(EXTRACT(EPOCH FROM (" + value1 + " - " + value2 + ")) / " + divideBy + ")::INT";
    }

    public String getDateTimeToDateCast(String columnName)
    {
        return "DATE(" + columnName + ")";
    }

    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + "::double precision)";
    }


    public void handleCreateDatabaseException(SQLException e) throws ServletException
    {
        if ("55006".equals(e.getSQLState()))
        {
            _log.error("You must close down pgAdmin III and all other applications accessing PostgreSQL.");
            throw(new ServletException("Close down or disconnect pgAdmin III and all other applications accessing PostgreSQL", e));
        }
        else
        {
            super.handleCreateDatabaseException(e);
        }
    }


    // Make sure that the PL/pgSQL language is enabled in the associated database.  If not, throw.  It would be nice
    // to CREATE LANGUAGE at this point, however, that requires SUPERUSER permissions and takes us down the path of
    // creating call handlers and other complexities.  It looks like PostgreSQL 8.1 has a simpler form of CREATE LANGUAGE...
    // once we require 8.1 we should consider using it here.
    public void prepareNewDatabase(DbSchema schema) throws ServletException
    {
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(schema, "SELECT * FROM pg_language WHERE lanname = 'plpgsql'", null);

            if (rs.next())
                return;

            String dbName = schema.getCatalog();
            String message = "PL/pgSQL is not enabled in the \"" + dbName + "\" database because it is not enabled in your Template1 master database.  Use PostgreSQL's 'createlang' command line utility to enable PL/pgSQL in the \"" + dbName + "\" database then restart Tomcat.";
            _log.error(message);
            throw new ServletException(message);
        }
        catch(SQLException e)
        {
             _log.error("Exception attempting to verify PL/pgSQL", e);
             throw new ServletException("Failure attempting to verify language PL/pgSQL", e);
        }
        finally
        {
            try {if (null != rs) rs.close();} catch(SQLException e) {_log.error("prepareNewDatabase", e);}
        }
    }


    // Do dialect-specific work after schema load, if necessary
    public void prepareNewDbSchema(DbSchema schema)
    {
        ResultSet rsSeq = null;
        try
        {
            // MAB: hacky, let's hope there is some system function that does the same thing
            rsSeq = Table.executeQuery(schema,
                    "SELECT relname, attname, adsrc\n" +
                            "FROM pg_attrdef \n" +
                            "\tJOIN pg_attribute ON pg_attrdef.adnum = pg_attribute.attnum AND pg_attrdef.adrelid=pg_attribute.attrelid \n" +
                            "\tJOIN pg_class on pg_attribute.attrelid=pg_class.oid \n" +
                            "\tJOIN pg_namespace ON pg_class.relnamespace=pg_namespace.oid\n" +
                            "WHERE nspname=?",
                    new Object[]{schema.getOwner()});

            while (rsSeq.next())
            {
                SchemaTableInfo t = schema.getTable(rsSeq.getString("relname"));
                if (null == t) continue;
                ColumnInfo c = t.getColumn(rsSeq.getString("attname"));
                if (null == c) continue;
                if (!c.isAutoIncrement())
                    continue;
                String src = rsSeq.getString("adsrc");
                int start = src.indexOf('\'');
                int end = src.lastIndexOf('\'');
                if (end > start)
                {
                    String sequence = src.substring(start + 1, end);
                    if (!sequence.toLowerCase().startsWith(schema.getOwner().toLowerCase() + "."))
                        sequence = schema.getOwner() + "." + sequence;
                    t.setSequence(sequence);
                }
            }
        }
        catch (Exception x)
        {
            _log.error("Error trying to find auto-increment sequences", x);
        }
        finally
        {
            ResultSetUtil.close(rsSeq);
        }
    }


    /**
     * Wrap one or more INSERT statements to allow explicit specification
     * of values for autoincrementing columns (e.g. IDENTITY in SQL Server
     * or SERIAL in Postgres). The input StringBuffer is modified.
     *
     * @param statements the insert statements. If more than one,
     *                   they must have been joined by appendStatement
     *                   and must all refer to the same table.
     * @param tinfo      table used in the insert(s)
     */
    public void overrideAutoIncrement(StringBuffer statements, TableInfo tinfo)
    {
        // Nothing special to do for the Postgres dialect
    }


    // Strip all comments -- PostgreSQL JDBC driver goes berserk if it sees ; or ?
    public int getNextSqlBlock(StringBuffer block, DbSchema schema, String sql, int start)
    {
        int j = start;

        while (j < sql.length())
        {
            char c = sql.charAt(j);
            String twoChars = null;
            int end = j + 1;

            if (j < (sql.length() - 1))
                twoChars = sql.substring(j, j + 2);

            if ('\'' == c)
            {
                end = sql.indexOf('\'', j + 1) + 1;

                if (0 == end)
                    _log.error("No quote termination char");
                else
                    block.append(sql.substring(j, end));
            }
            else if ("/*".equals(twoChars))
            {
                end = sql.indexOf("*/", j + 2) + 2;  // Skip comment completely

                if (1 == end)
                    _log.error("No comment termination char");
            }
            else if ("--".equals(twoChars))
            {
                end = sql.indexOf("\n", j + 2) + 1;  // Skip comment completely

                if (0 == end)
                    end = sql.length();
            }
            else
                block.append(c);

            j = end;
        }

        return sql.length();
    }


    void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
        if (lowerNoWhiteSpace.contains("setsearch_pathto"))
            errors.add("Do not use \"SET search_path TO <schema>\".  Instead, schema-qualify references to all objects.");

        if (!lowerNoWhiteSpace.endsWith(";"))
            errors.add("Script should end with a semicolon");
    }


    public String getMasterDataBaseName()
    {
        return "template1";
    }


    /*  PostgreSQL example connection URLs we need to parse:

        jdbc:postgresql:database
        jdbc:postgresql://host/database
        jdbc:postgresql://host:port/database
        jdbc:postgresql:database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host/database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host:port/database?user=fred&password=secret&ssl=true

    */
    public static class PostgreSQLJdbcHelper extends JdbcHelper
    {
        protected PostgreSQLJdbcHelper(String url)
        {
            int dbEnd = url.indexOf('?');
            if (-1 == dbEnd)
                dbEnd = url.length();
            int dbDelimiter = url.lastIndexOf('/', dbEnd);
            if (-1 == dbDelimiter)
                dbDelimiter = url.lastIndexOf(':', dbEnd);
            _database = url.substring(dbDelimiter + 1, dbEnd);
        }
    }

    protected String getDatabaseMaintenanceSql()
    {
        return "VACUUM ANALYZE;";
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment(" POSITION(");
        ret.append(littleString);
        ret.append(" IN ");
        ret.append(bigString);
        ret.append(") ");
        return ret;
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        SQLFragment tmp = new SQLFragment("position(");
        tmp.append(littleString);
        tmp.append(" in substring(");
        tmp.append(bigString);
        tmp.append(" from ");
        tmp.append(startIndex);
        tmp.append("))");
        SQLFragment ret = new SQLFragment("((");
        ret.append(startIndex);
        // TODO: code review this: I believe that this -1 is necessary to produce the correct results.
        ret.append(" - 1)");
        ret.append(" * sign(");
        ret.append(tmp);
        ret.append(")+");
        ret.append(tmp);
        ret.append(")");
        return ret;
    }

    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true;
    }

    static final private Pattern s_patStringLiteral = Pattern.compile("\\'([^\\\\\\']|(\\'\\')|(\\\\.))*\\'");
    protected Pattern patStringLiteral()
    {
        return s_patStringLiteral;
    }

    protected String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(StringUtils.replace(str, "\\", "\\\\"), "'", "''") + "'";
    }
}
