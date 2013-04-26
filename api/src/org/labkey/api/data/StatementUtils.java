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
package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasManager;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: 2/26/12
 * Time: 3:54 PM
 */

// I pulled these methods out of Table.java in an attempt get Clover to provide coverage information on them. (Clover seems
// to skip any class that includes a junit TestCase.) I'm looking to refactor the re-select behavior, but want Cover to
// identify tests that exercise the code paths that will be changed.
public class StatementUtils
{
    private enum operation {insert, update, merge}


    /**
     * Create a reusable SQL Statement for inserting rows into an labkey relationship.  The relationship
     * persisted directly in the database (SchemaTableInfo), or via the OnotologyManager tables.
     *
     * QueryService shouldn't really know about the internals of exp.Object and exp.ObjectProperty etc.
     * However, I can only keep so many levels of abstraction in my head at once.
     *
     * NOTE: this is currently fairly expensive for updating one row into an Ontology stored relationship on Postgres.
     * This shouldn't be a big problem since we don't usually need to optimize the one row case, and we're moving
     * to provisioned tables for major datatypes.
     */
    public static Parameter.ParameterMap insertStatement(Connection conn, TableInfo table, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return new StatementUtils(table).createStatement(conn, table, null, c, user, selectIds, autoFillDefaultColumns, operation.insert);
    }

    public static Parameter.ParameterMap insertStatement(Connection conn, TableInfo table, @Nullable Set<String> skipColumnNames, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return new StatementUtils(table).createStatement(conn, table, skipColumnNames, c, user, selectIds, autoFillDefaultColumns, operation.insert);
    }


    /**
     * Create a reusable SQL Statement for updating rows into an labkey relationship.  The relationship
     * persisted directly in the database (SchemaTableInfo), or via the OnotologyManager tables.
     *
     * QueryService shouldn't really know about the internals of exp.Object and exp.ObjectProperty etc.
     * However, I can only keep so many levels of abstraction in my head at once.
     *
     * NOTE: this is currently fairly expensive for updating one row into an Ontology stored relationship on Postgres.
     * This shouldn't be a big problem since we don't usually need to optimize the one row case, and we're moving
     * to provisioned tables for major datatypes.
     */
    public static Parameter.ParameterMap updateStatement(Connection conn, TableInfo table, @Nullable Container c, User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return new StatementUtils(table).createStatement(conn, table, null, c, user, selectIds, autoFillDefaultColumns, operation.update);
    }


//    public static Parameter.ParameterMap mergeStatement(Connection conn, TableInfo table, @Nullable Set<String> skipColumnNames, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
//    {
//        return new StatementUtils(table).createStatement(conn, table, skipColumnNames, c, user, selectIds, autoFillDefaultColumns, operation.merge);
//    }



    /*
     * Parameter and Variable helpers
     */

    boolean useVariables = false;
    SqlDialect dialect;
    CaseInsensitiveHashMap<Parameter> parameters = new CaseInsensitiveHashMap<>();

    private String makeVariableName(String name)
    {
        return (dialect.isSqlServer() ? "@p" : "_$p") + (parameters.size()+1) + AliasManager.makeLegalName(name, null);
    }


    private Parameter createParameter(ColumnInfo c)
    {
        Parameter p = parameters.get(c.getName());
        if (null == p)
        {
            p = new Parameter(c, null);
            p.setVariableName(makeVariableName(c.getName()));
            parameters.put(c.getName(), p);
        }
        return p;
    }


    private Parameter createParameter(String name, JdbcType type)
    {
        Parameter p = parameters.get(name);
        if (null == p)
        {
            p = new Parameter(name, type);
            p.setVariableName(makeVariableName(name));
            parameters.put(name, p);
        }
        return p;
    }


    private Parameter createParameter(String name, String uri, JdbcType type)
    {
        Parameter p = parameters.get(name);
        if (null == p)
        {
            p = new Parameter(name, uri, null, type);
            p.setVariableName(makeVariableName(name));
            parameters.put(name, p);
        }
        return p;
    }


    public SQLFragment appendParameterOrVariable(SQLFragment f, Parameter p)
    {
        if (useVariables)
        {
            f.append(p.getVariableName());
        }
        else
        {
            f.append("?");
            f.add(p);
        }
        return f;
    }

    public SQLFragment appendParameterOrVariableOrConstant(SQLFragment f, Parameter p, String literal)
    {
        if (null == literal)
            return appendParameterOrVariable(f, p);
        f.append(literal);
        return f;
    }


    public SQLFragment appendParameterOrVariable(SQLFragment f, ColumnInfo col)
    {
        Parameter p = createParameter(col);
        return appendParameterOrVariable(f, p);
    }


    public SQLFragment appendPropertyValue(SQLFragment f, DomainProperty dp, Parameter p)
    {
        if (JdbcType.valueOf(dp.getSqlType()) == JdbcType.BOOLEAN)
        {
            f.append("CASE CAST(");
            appendParameterOrVariable(f, p);
            f.append(" AS "+ dialect.getBooleanDataType() + ")" +
                    " WHEN " + dialect.getBooleanTRUE() + " THEN 1.0 " +
                    " WHEN " + dialect.getBooleanFALSE() + " THEN 0.0" +
                    " ELSE NULL END");
            return f;
        }
        else
        {
            return appendParameterOrVariable(f, p);
        }
    }


    private StatementUtils(TableInfo t)
    {
        dialect = t.getSqlDialect();
    }


    private Parameter.ParameterMap createStatement(Connection conn, TableInfo t, @Nullable Set<String> skipColumnNames, @Nullable Container c, User user, boolean selectIds, boolean autoFillDefaultColumns, operation op) throws SQLException
    {
        if (!(t instanceof UpdateableTableInfo))
            throw new IllegalArgumentException("Table must be an UpdatedableTableInfo");

        UpdateableTableInfo updatable = (UpdateableTableInfo)t;
        TableInfo table = updatable.getSchemaTableInfo();

        if (!(table instanceof SchemaTableInfo))
            throw new IllegalArgumentException();
        if (null == ((SchemaTableInfo)table).getMetaDataName())
            throw new IllegalArgumentException();

        if (operation.merge == op)
        {
            if (!dialect.isPostgreSQL() && !dialect.isSqlServer())
                throw new IllegalArgumentException("Merge is only supported/tested on postgres and sql server");
        }

        useVariables = operation.merge == op && dialect.isPostgreSQL();
        String ifTHEN = dialect.isSqlServer() ? " BEGIN " : " THEN ";
        String ifEND = dialect.isSqlServer() ? " END " : "; END IF ";
        String containerIdConstant = null==c ? null : "'" + c.getId() + "'";

        Timestamp ts = new Timestamp(System.currentTimeMillis());
        Parameter containerParameter = null;
        String objectURIColumnName = updatable.getObjectUriType() == UpdateableTableInfo.ObjectUriType.schemaColumn
                ? updatable.getObjectURIColumnName()
                : "objecturi";
        Parameter objecturiParameter = null;
        if (null != objectURIColumnName)
            objecturiParameter = createParameter(objectURIColumnName, JdbcType.VARCHAR);

        String comma;
        Set<String> done = Sets.newCaseInsensitiveHashSet();

        String objectIdVar = null;
        String rowIdVar = null;
        String setKeyword = dialect.isPostgreSQL() ? "" : "SET ";

        //
        // Keys for UPDATE or MERGE
        //

/*        // TODO more control/configuration
        // using objectURIColumnName preferentially to be backward compatible with OntologyManager.saveTabDelimited
        // but that usage should probably go away
        HashSet<FieldKey> keys = new LinkedHashSet<>();
        ColumnInfo col = table.getColumn("Container");
        if (null != col)
            keys.add(col.getFieldKey());
        col = table.getColumn(objectURIColumnName);
        if (null != col)
            keys.add(col.getFieldKey());
        else
        {
            for (ColumnInfo pk : table.getPkColumns())
                keys.add(pk.getFieldKey());
        }
*/

        //
        // exp.Objects INSERT
        //


        SQLFragment sqlfDeclare = new SQLFragment();
        SQLFragment sqlfInsertObject = new SQLFragment();
        SQLFragment sqlfSelectObject = new SQLFragment();
        SQLFragment sqlfObjectProperty = new SQLFragment();
        SQLFragment sqlfDelete = new SQLFragment();

        Domain domain = t.getDomain();
        DomainKind domainKind = t.getDomainKind();
        DomainProperty[] properties = null;

        if (null != domain && null != domainKind && StringUtils.isEmpty(domainKind.getStorageSchemaName()))
        {
            properties = domain.getProperties();

            if (properties.length == 0)
                properties = null;

            if (null != properties)
            {
                if (!dialect.isPostgreSQL() && !dialect.isSqlServer())
                    throw new IllegalStateException("Domains are only supported for sql server and postgres");
                if (operation.merge == op)
                    throw new IllegalStateException("Merge is not tested for extensible tables yet");

                objectIdVar = dialect.isPostgreSQL() ? "_$objectid$_" : "@_objectid_";
                useVariables = dialect.isPostgreSQL();
                if (!sqlfDeclare.isEmpty())
                    sqlfDeclare.append(";\n");
                sqlfDeclare.append("DECLARE ").append(objectIdVar).append(" INT");

                if (null == c)
                    containerParameter = createParameter("container", JdbcType.VARCHAR);
//                if (autoFillDefaultColumns && null != c)
//                    containerParameter.setValue(c.getId(), true);

                // Insert a new row in exp.Object if there isn't already a row for this object

                // In the update case, it's still possible that there isn't a row in exp.Object - there might have been
                // no properties in the domain when the row was originally inserted
                sqlfInsertObject.append("INSERT INTO exp.Object (container, objecturi) ");
                sqlfInsertObject.append("SELECT ");
                appendParameterOrVariableOrConstant(sqlfInsertObject, containerParameter, containerIdConstant);
                sqlfInsertObject.append(" AS Container,");
                appendParameterOrVariable(sqlfInsertObject, objecturiParameter);
                sqlfInsertObject.append(" AS ObjectURI WHERE NOT EXISTS (SELECT ObjectURI FROM exp.Object WHERE Container = ");
                appendParameterOrVariableOrConstant(sqlfInsertObject, containerParameter, containerIdConstant);
                sqlfInsertObject.append(" AND ObjectURI = ");
                appendParameterOrVariable(sqlfInsertObject, objecturiParameter);
                sqlfInsertObject.append(")");

                // Grab the object's ObjectId
                sqlfSelectObject.append(setKeyword).append(objectIdVar).append(" = (");
                sqlfSelectObject.append("SELECT ObjectId FROM exp.Object WHERE Container = ");
                appendParameterOrVariableOrConstant(sqlfSelectObject, containerParameter, containerIdConstant);
                sqlfSelectObject.append(" AND ObjectURI = ");
                appendParameterOrVariable(sqlfSelectObject, objecturiParameter);
                sqlfSelectObject.append(")");

                if (operation.insert != op)
                {
                    // Clear out any existing property values for this domain
                    sqlfDelete.append("DELETE FROM exp.ObjectProperty WHERE ObjectId = ");
                    sqlfDelete.append(objectIdVar);
                    sqlfDelete.append(" AND PropertyId IN (");
                    String separator = "";
                    for (DomainProperty property : properties)
                    {
                        sqlfDelete.append(separator);
                        separator = ", ";
                        sqlfDelete.append(property.getPropertyId());
                    }
                    sqlfDelete.append(")");
                }
            }
        }

        //
        // BASE TABLE INSERT()
        //

        List<SQLFragment> cols = new ArrayList<>();
        List<SQLFragment> values = new ArrayList<>();

        ColumnInfo col = table.getColumn("Container");
        if (null != col)
        {
            cols.add(new SQLFragment("Container"));
            if (null == containerParameter && null == c)
                containerParameter = createParameter("container", JdbcType.VARCHAR);
            values.add(appendParameterOrVariableOrConstant(new SQLFragment(), containerParameter, containerIdConstant));
            done.add("Container");
        }

        if (autoFillDefaultColumns && operation.update != op)
        {
            col = table.getColumn("Owner");
            if (null != col && null != user)
            {
                cols.add(new SQLFragment("Owner"));
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("Owner");
            }
            col = table.getColumn("CreatedBy");
            if (null != col && null != user)
            {
                cols.add(new SQLFragment("CreatedBy"));
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("CreatedBy");
            }
            col = table.getColumn("Created");
            if (null != col)
            {
                cols.add(new SQLFragment("Created"));
                values.add(new SQLFragment("CAST('" + ts + "' AS " + dialect.getDefaultDateTimeDataType() + ")"));
                done.add("Created");
            }
        }

        ColumnInfo colModifiedBy = table.getColumn("Modified");

        if (autoFillDefaultColumns && null != colModifiedBy && null != user)
        {
            cols.add(new SQLFragment("ModifiedBy"));
            values.add(new SQLFragment().append(user.getUserId()));
            done.add("ModifiedBy");
        }

        ColumnInfo colModified = table.getColumn("Modified");

        if (autoFillDefaultColumns && null != colModified)
        {
            cols.add(new SQLFragment("Modified"));
            values.add(new SQLFragment("CAST('" + ts + "' AS " + dialect.getDefaultDateTimeDataType() + ")"));
            done.add("Modified");
        }
        ColumnInfo colVersion = table.getVersionColumn();
        if (autoFillDefaultColumns && null != colVersion && !done.contains(colVersion.getName()) && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
        {
            cols.add(new SQLFragment(colVersion.getSelectName()));
            values.add(new SQLFragment("CAST('" + ts + "' AS " + dialect.getDefaultDateTimeDataType() + ")"));
            done.add(colVersion.getName());
        }

        String objectIdColumnName = StringUtils.trimToNull(updatable.getObjectIdColumnName());
        ColumnInfo autoIncrementColumn = null;
        CaseInsensitiveHashMap<String> remap = updatable.remapSchemaColumns();
        if (null == remap)
            remap = new CaseInsensitiveHashMap<>();

        for (ColumnInfo column : table.getColumns())
        {
            if (column instanceof WrappedColumn)
                continue;
            if (column.isAutoIncrement())
            {
                autoIncrementColumn = column;
                continue;
            }
            if (column.isVersionColumn() && column != colModified)
                continue;
            String name = column.getName();
            if (done.contains(name))
                continue;
            done.add(name);

            SQLFragment valueSQL = new SQLFragment();
            if (column.getName().equalsIgnoreCase(objectIdColumnName))
            {
                valueSQL.append(objectIdVar);
            }
            else if (column.getName().equalsIgnoreCase(updatable.getObjectURIColumnName()) && null != objecturiParameter)
            {
                appendParameterOrVariable(valueSQL, objecturiParameter);
            }
            else
            {
                if (null != skipColumnNames && skipColumnNames.contains(StringUtils.defaultString(remap.get(name),name)))
                    continue;
                Parameter p = createParameter(column);
                appendParameterOrVariable(valueSQL, p);
            }
            cols.add(new SQLFragment(column.getSelectName()));
            values.add(valueSQL);
        }

        SQLFragment sqlfSelectIds = null;
        boolean selectAutoIncrement = false;

        SQLFragment sqlfInsertInto = new SQLFragment();
        SQLFragment sqlfUpdate = new SQLFragment();

        assert cols.size() == values.size() : cols.size() + " columns and " + values.size() + " values - should match";

        if (operation.insert == op || operation.merge == op)
        {
            // Create a standard INSERT INTO table (col1, col2) VALUES (val1, val2) statement
            // or (for degenerate, empty values case) INSERT INTO table VALUES (DEFAULT)
            sqlfInsertInto.append("INSERT INTO ").append(table.getSelectName());

            if (!values.isEmpty())
            {
                sqlfInsertInto.append(" (");
                comma = "";
                for (SQLFragment colSQL : cols)
                {
                    sqlfInsertInto.append(comma);
                    comma = ", ";
                    sqlfInsertInto.append(colSQL);
                }
                sqlfInsertInto.append(")");
            }

            sqlfInsertInto.append("\nVALUES (");

            if (values.isEmpty())
            {
                sqlfInsertInto.append("DEFAULT");
            }
            else
            {
                comma = "";

                for (SQLFragment valueSQL : values)
                {
                    sqlfInsertInto.append(comma);
                    comma = ", ";
                    sqlfInsertInto.append(valueSQL);
                }
            }

            sqlfInsertInto.append(")");

            if (selectIds && null != autoIncrementColumn)
            {
                selectAutoIncrement = true;
                if (null != objectIdVar)
                {
                    rowIdVar = dialect.isPostgreSQL() ? "_$rowid$_" : "@_rowid_";
                    if (!sqlfDeclare.isEmpty())
                        sqlfDeclare.append(";\n");
                    sqlfDeclare.append("DECLARE ").append(rowIdVar).append(" INT");
                }
                dialect.appendSelectAutoIncrement(sqlfInsertInto, table, autoIncrementColumn.getName(), rowIdVar);
            }
        }
        if (operation.update == op || operation.merge == op)
        {
            // Create a standard UPDATE table SET col1 = val1, col2 = val2 statement
            sqlfUpdate.append("UPDATE ").append(table.getSelectName()).append(" SET ");
            comma = "";
            for (int i = 0; i < cols.size(); i++)
            {
                sqlfUpdate.append(comma);
                comma = ", ";
                sqlfUpdate.append(cols.get(i));
                sqlfUpdate.append(" = ");
                sqlfUpdate.append(values.get(i));
            }
            sqlfUpdate.append(" WHERE ");
            sqlfUpdate.append(objectURIColumnName);
            sqlfUpdate.append(" = ");
            appendParameterOrVariable(sqlfUpdate, objecturiParameter);
        }


        if (selectIds && (null != objectIdVar || null != rowIdVar))
        {
            sqlfSelectIds = new SQLFragment("SELECT ");
            comma = "";
            if (null != rowIdVar)
            {
                sqlfSelectIds.append(rowIdVar);
                comma = ",";
            }
            if (null != objectIdVar)
                sqlfSelectIds.append(comma).append(objectIdVar);
        }


        //
        // ObjectProperty
        //

        if (null != properties)
        {
            Set<String> skip = updatable.skipProperties();
            if (null != skip)
                done.addAll(skip);

            String stmtSep = "";
            for (DomainProperty dp : properties)
            {
                // ignore property that 'wraps' a hard column
                if (done.contains(dp.getName()))
                    continue;
                PropertyType propertyType = dp.getPropertyDescriptor().getPropertyType();
                Parameter v = createParameter(dp.getName(), dp.getPropertyURI(), propertyType.getJdbcType());
                Parameter mv = createParameter(dp.getName()+ MvColumn.MV_INDICATOR_SUFFIX, dp.getPropertyURI() + MvColumn.MV_INDICATOR_SUFFIX, JdbcType.VARCHAR);
                sqlfObjectProperty.append(stmtSep);
                stmtSep = ";\n";
                sqlfObjectProperty.append("IF (");
                appendPropertyValue(sqlfObjectProperty, dp, v);
                sqlfObjectProperty.append(" IS NOT NULL");
                if (dp.isMvEnabled())
                {
                    sqlfObjectProperty.append(" OR ");
                    appendParameterOrVariable(sqlfObjectProperty, mv);
                    sqlfObjectProperty.append(" IS NOT NULL");
                }
                sqlfObjectProperty.append(")");
                sqlfObjectProperty.append(ifTHEN);
                sqlfObjectProperty.append("INSERT INTO exp.ObjectProperty (objectid, propertyid, typetag, mvindicator, ");
                switch (propertyType.getStorageType())
                {
                    case 's':
                        sqlfObjectProperty.append("stringValue");
                        break;
                    case 'd':
                        sqlfObjectProperty.append("dateTimeValue");
                        break;
                    case 'f':
                        sqlfObjectProperty.append("floatValue");
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown property type: " + propertyType);
                }
                sqlfObjectProperty.append(") VALUES (");
                sqlfObjectProperty.append(objectIdVar);
                sqlfObjectProperty.append(",").append(dp.getPropertyId());
                sqlfObjectProperty.append(",'").append(propertyType.getStorageType()).append("'");
                sqlfObjectProperty.append(",");
                if (dp.isMvEnabled())
                    appendParameterOrVariable(sqlfObjectProperty, mv);
                else
                    sqlfObjectProperty.append("NULL");
                sqlfObjectProperty.append(",");
                appendPropertyValue(sqlfObjectProperty, dp, v);
                sqlfObjectProperty.append(")");
                sqlfObjectProperty.append(ifEND);
            }
        }

        //
        // PREPARE
        //

        Parameter.ParameterMap ret;

        if (!useVariables)
        {
            SQLFragment script = new SQLFragment();
            script.appendStatement(sqlfDeclare, dialect);
            script.appendStatement(sqlfInsertObject, dialect);
            script.appendStatement(sqlfSelectObject, dialect);
            script.appendStatement(sqlfDelete, dialect);
            script.appendStatement(sqlfUpdate, dialect);
            script.appendStatement(sqlfInsertInto, dialect);
            script.appendStatement(sqlfObjectProperty, dialect);
            script.appendStatement(sqlfSelectIds, dialect);

            ret = new Parameter.ParameterMap(table.getSchema().getScope(), conn, script, remap);
        }
        else
        {
            // wrap in a function
            SQLFragment fn = new SQLFragment();
            String fnName = dialect.getGlobalTempTablePrefix() + "fn_" + GUID.makeHash();
            fn.append("CREATE FUNCTION ").append(fnName).append("(");
            // TODO d.execute() doesn't handle temp schema
            SQLFragment call = new SQLFragment();
            call.append(fnName).append("(");
            final SQLFragment drop = new SQLFragment("DROP FUNCTION " + fnName + "(");
            comma = "";
            for (Map.Entry<String, Parameter> e : parameters.entrySet())
            {
                Parameter p = e.getValue();
                String variable = p.getVariableName();
                String type = dialect.sqlTypeNameFromSqlType(p.getType().sqlType);
                fn.append("\n").append(comma);
                fn.append(variable);
                fn.append(" ");
                fn.append(type);
                fn.append(" -- ").append(AliasManager.makeLegalName(p.getName(), null, false));
                drop.append(comma).append(type);
                call.append(comma).append("?");
                call.add(p);
                comma = ",";
            }
            fn.append("\n) RETURNS ");
            if (null != sqlfSelectIds)
                fn.append("SETOF RECORD");
            else
                fn.append("void");
            fn.append(" AS $$\n");
            drop.append(");");
            call.append(")");


            if (null != sqlfSelectIds)
            {
                call.insert(0, "SELECT * FROM ");
                if (null != rowIdVar && null != objectIdVar)
                    call.append(" AS x(A int, B int)");
                else
                    call.append(" AS x(A int)");
                call.append(";");
            }
            else
            {
                call.insert(0, "{call ");
                call.append("}");
            }

            // Append these by hand -- don't want ; in the middle of the function
            fn.append(sqlfDeclare);

            // Treat as one statement -- don't want ; inbetween
            sqlfInsertObject.insert(0, "BEGIN\n");

            fn.appendStatement(sqlfInsertObject, dialect);
            fn.appendStatement(sqlfSelectObject, dialect);
            fn.appendStatement(sqlfDelete, dialect);
            fn.appendStatement(sqlfUpdate, dialect);
            fn.appendStatement(sqlfInsertInto, dialect);

            fn.appendStatement(sqlfObjectProperty, dialect);
            if (null == sqlfSelectIds)
            {
                fn.appendStatement(new SQLFragment("RETURN"), dialect);
            }
            else
            {
                sqlfSelectIds.insert(0, "RETURN QUERY ");
                fn.appendStatement(sqlfSelectIds, dialect);
            }
            fn.append(";\nEND;\n$$ LANGUAGE plpgsql;\n");
            new SqlExecutor(table.getSchema()).execute(fn);
            ret = new Parameter.ParameterMap(table.getSchema().getScope(), conn, call, updatable.remapSchemaColumns());
            ret.onClose(new Runnable() { @Override public void run()
            {
                try
                {
                    new SqlExecutor(ExperimentService.get().getSchema()).execute(drop);
                }
                catch (Exception x)
                {
                    Logger.getLogger(Table.class).error("Error dropping temp function.", x);
                }
            }});
        }

//        if (null != constants)
//        {
//            for (Map.Entry e : constants.entrySet())
//            {
//                Parameter p = ret._map.get(e.getKey());
//                if (null != p)
//                    p.setValue(e.getValue(), true);
//            }
//        }

        if (selectIds)
        {
            // Why is one of these boolean and the other an index?? I don't know
            ret.setSelectRowId(selectAutoIncrement);
            if (null != objectIdVar)
                ret.setObjectIdIndex(selectAutoIncrement ? 2 : 1);
        }

        return ret;
    }


    public static class TestCase extends Assert
    {
        TableInfo issues;
        User user;
        Container container;

        void init()
        {
            issues = DbSchema.get("issues").getTable("issues");
            container = JunitUtil.getTestContainer();
            user = TestContext.get().getUser();
        }


        @Test
        public void testInsert() throws Exception
        {
            init();
            try (Connection conn = issues.getSchema().getScope().getConnection())
            {
                Parameter.ParameterMap m = StatementUtils.insertStatement(conn, issues, null, container, user, true, true);
                System.err.println(m.getDebugSql());
            }
        }


        @Test
        public void testUpdate() throws Exception
        {
            init();
            try (Connection conn = issues.getSchema().getScope().getConnection())
            {
                Parameter.ParameterMap m = StatementUtils.updateStatement(conn, issues, container, user, true, true);
                System.err.println(m.getDebugSql());
            }
        }


        @Test
        public void testMerge() throws Exception
        {
            init();
            try (Connection conn = issues.getSchema().getScope().getConnection())
            {
            }
        }
    }
}
