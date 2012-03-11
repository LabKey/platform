/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.collections.Sets;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
    @Deprecated
    private static void appendSelectAutoIncrement(SqlDialect d, SQLFragment sqlf, TableInfo tinfo, String columnName)
    {
        String t = d.appendSelectAutoIncrement("", tinfo, columnName);
        t = StringUtils.strip(t, ";\n\r");
        sqlf.append(t);
    }


    // Consider: use in other places?
    public static SQLFragment appendParameterOrVariable(SQLFragment f, SqlDialect d, boolean useVariable, Parameter p, Map<Parameter, String> names)
    {
        if (!useVariable)
        {
            f.append("?");
            f.add(p);
        }
        else
        {
            String v = names.get(p);
            if (null == v)
            {
                v =  (d.isSqlServer() ? "@p" : "_$p") + (names.size()+1);
                names.put(p,v);
            }
            f.append(v);
        }
        return f;
    }

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
    public static Parameter.ParameterMap insertStatement(Connection conn, TableInfo table, @Nullable Container c, User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return createStatement(conn, table, c, user, selectIds, autoFillDefaultColumns, true);
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
        return createStatement(conn, table, c, user, selectIds, autoFillDefaultColumns, false);
    }

    public static Parameter.ParameterMap createStatement(Connection conn, TableInfo t, @Nullable Container c, User user, boolean selectIds, boolean autoFillDefaultColumns, boolean insert) throws SQLException
    {
        if (!(t instanceof UpdateableTableInfo))
            throw new IllegalArgumentException("Table must be an UpdatedableTableInfo");

        UpdateableTableInfo updatable = (UpdateableTableInfo)t;
        TableInfo table = updatable.getSchemaTableInfo();

        if (!(table instanceof SchemaTableInfo))
            throw new IllegalArgumentException();
        if (null == ((SchemaTableInfo)table).getMetaDataName())
            throw new IllegalArgumentException();

        SqlDialect d = t.getSqlDialect();
        boolean useVariables = false;

        // helper for generating procedure/function variation
        Map<Parameter,String> parameterToVariable = new IdentityHashMap<Parameter,String>();

        Timestamp ts = new Timestamp(System.currentTimeMillis());
        Parameter containerParameter = null;
        String objectURIColumnName = updatable.getObjectUriType() == UpdateableTableInfo.ObjectUriType.schemaColumn
                ? updatable.getObjectURIColumnName()
                : "objecturi";
        Parameter objecturiParameter = new Parameter(objectURIColumnName, JdbcType.VARCHAR);

        String comma;
        Set<String> done = Sets.newCaseInsensitiveHashSet();

        String objectIdVar = null;
        String setKeyword = d.isPostgreSQL() ? "" : "SET ";

        //
        // exp.Objects INSERT
        //

        SQLFragment sqlfDeclare = new SQLFragment();
        SQLFragment sqlfObject = new SQLFragment();
        SQLFragment sqlfObjectProperty = new SQLFragment();

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
                if (!d.isPostgreSQL() && !d.isSqlServer())
                    throw new IllegalArgumentException("Domains are only supported for sql server and postgres");

                objectIdVar = d.isPostgreSQL() ? "_$objectid$_" : "@_objectid_";

                useVariables = d.isPostgreSQL();
                sqlfDeclare.append("DECLARE ").append(objectIdVar).append(" INT;\n");
                containerParameter = new Parameter("container", JdbcType.VARCHAR);
//                if (autoFillDefaultColumns && null != c)
//                    containerParameter.setValue(c.getId(), true);

                // Insert a new row in exp.Object if there isn't already a row for this object

                // In the update case, it's still possible that there isn't a row in exp.Object - there might have been
                // no properties in the domain when the row was originally inserted
                sqlfObject.append("INSERT INTO exp.Object (container, objecturi) ");
                sqlfObject.append("SELECT ");
                appendParameterOrVariable(sqlfObject, d, useVariables, containerParameter, parameterToVariable);
                sqlfObject.append(" AS ObjectURI,");
                appendParameterOrVariable(sqlfObject, d, useVariables, objecturiParameter, parameterToVariable);
                sqlfObject.append(" AS Container WHERE NOT EXISTS (SELECT ObjectURI FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfObject, d, useVariables, containerParameter, parameterToVariable);
                sqlfObject.append(" AND ObjectURI = ");
                appendParameterOrVariable(sqlfObject, d, useVariables, objecturiParameter, parameterToVariable);
                sqlfObject.append(");\n");

                // Grab the object's ObjectId
                sqlfObject.append(setKeyword).append(objectIdVar).append(" = (");
                sqlfObject.append("SELECT ObjectId FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfObject, d, useVariables, containerParameter, parameterToVariable);
                sqlfObject.append(" AND ObjectURI = ");
                appendParameterOrVariable(sqlfObject, d, useVariables, objecturiParameter, parameterToVariable);
                sqlfObject.append(");\n");

                if (!insert)
                {
                    // Clear out any existing property values for this domain
                    sqlfObject.append("DELETE FROM exp.ObjectProperty WHERE ObjectId = ");
                    sqlfObject.append(objectIdVar);
                    sqlfObject.append(" AND PropertyId IN (");
                    String separator = "";
                    for (DomainProperty property : properties)
                    {
                        sqlfObject.append(separator);
                        separator = ", ";
                        sqlfObject.append(property.getPropertyId());
                    }
                    sqlfObject.append(");\n");
                }
            }
        }

        //
        // BASE TABLE INSERT()
        //

        List<SQLFragment> cols = new ArrayList<SQLFragment>();
        List<SQLFragment> values = new ArrayList<SQLFragment>();
        ColumnInfo col;

        col = table.getColumn("Container");
        if (null != col && null != user)
        {
            cols.add(new SQLFragment("Container"));
            if (null == containerParameter)
            {
                containerParameter = new Parameter("container", JdbcType.VARCHAR);
//                if (autoFillDefaultColumns && null != c)
//                    containerParameter.setValue(c.getId(), true);
            }
            values.add(appendParameterOrVariable(new SQLFragment(), d, useVariables, containerParameter, parameterToVariable));
            done.add("Container");
        }
        if (insert)
        {
            col = table.getColumn("Owner");
            if (autoFillDefaultColumns && null != col && null != user)
            {
                cols.add(new SQLFragment("Owner"));
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("Owner");
            }
            col = table.getColumn("CreatedBy");
            if (autoFillDefaultColumns && null != col && null != user)
            {
                cols.add(new SQLFragment("CreatedBy"));
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("CreatedBy");
            }
            col = table.getColumn("Created");
            if (autoFillDefaultColumns && null != col)
            {
                cols.add(new SQLFragment("Created"));
                values.add(new SQLFragment("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")"));
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
            values.add(new SQLFragment("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")"));
            done.add("Modified");
        }
        ColumnInfo colVersion = table.getVersionColumn();
        if (autoFillDefaultColumns && null != colVersion && !done.contains(colVersion.getName()) && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
        {
            cols.add(new SQLFragment(colVersion.getSelectName()));
            values.add(new SQLFragment("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")"));
            done.add(colVersion.getName());
        }

        String objectIdColumnName = StringUtils.trimToNull(updatable.getObjectIdColumnName());
        ColumnInfo autoIncrementColumn = null;

        for (ColumnInfo column : table.getColumns())
        {
            if (column.isAutoIncrement())
            {
                autoIncrementColumn = column;
                continue;
            }
            if (column.isVersionColumn())
                continue;
            if (done.contains(column.getName()))
                continue;
            done.add(column.getName());

            cols.add(new SQLFragment(column.getSelectName()));
            SQLFragment valueSQL = new SQLFragment();
            if (column.getName().equalsIgnoreCase(objectIdColumnName))
            {
                valueSQL.append(objectIdVar);
            }
            else if (column.getName().equalsIgnoreCase(updatable.getObjectURIColumnName()) && null != objecturiParameter)
            {
                appendParameterOrVariable(valueSQL, d, useVariables, objecturiParameter, parameterToVariable);
            }
            else
            {
                Parameter p = new Parameter(column, null);
                appendParameterOrVariable(valueSQL, d, useVariables, p, parameterToVariable);
            }
            values.add(valueSQL);
        }

        SQLFragment sqlfSelectIds = null;
        Integer selectRowIdIndex = null;
        Integer selectObjectIdIndex = null;
        int countReturnIds = 0;

        if (selectIds && (null != autoIncrementColumn || null != objectIdVar))
        {
            sqlfSelectIds = new SQLFragment("");
            String prefix = "SELECT ";

            if (null != autoIncrementColumn)
            {
                appendSelectAutoIncrement(d, sqlfSelectIds, table, autoIncrementColumn.getName());
                selectRowIdIndex = ++countReturnIds;
                prefix = ", ";
            }

            if (null != objectIdVar)
            {
                Logger.getLogger(StatementUtils.class).info("Code path that selects object IDs");
                sqlfSelectIds.append(prefix);
                sqlfSelectIds.append(objectIdVar);
                selectObjectIdIndex = ++countReturnIds;
            }

            sqlfSelectIds.append(";\n");
        }

        SQLFragment sqlfInsertInto = new SQLFragment();

        assert cols.size() == values.size() : cols.size() + " columns and " + values.size() + " values - should match";

        if (insert)
        {
            // Create a standard INSERT INTO table (col1, col2) VALUES (val1, val2) statement
            sqlfInsertInto.append("INSERT INTO ").append(table).append(" (");
            comma = "";
            for (SQLFragment colSQL : cols)
            {
                sqlfInsertInto.append(comma);
                comma = ", ";
                sqlfInsertInto.append(colSQL);
            }
            sqlfInsertInto.append(")\nVALUES (");
            comma = "";
            for (SQLFragment valueSQL : values)
            {
                sqlfInsertInto.append(comma);
                comma = ", ";
                sqlfInsertInto.append(valueSQL);
            }
            sqlfInsertInto.append(");\n");
        }
        else
        {
            // Create a standard UPDATE table SET col1 = val1, col2 = val2 statement
            sqlfInsertInto.append("UPDATE ").append(table).append(" SET ");
            comma = "";
            for (int i = 0; i < cols.size(); i++)
            {
                sqlfInsertInto.append(comma);
                comma = ", ";
                sqlfInsertInto.append(cols.get(i));
                sqlfInsertInto.append(" = ");
                sqlfInsertInto.append(values.get(i));
            }
            sqlfInsertInto.append(" WHERE ");
            sqlfInsertInto.append(objectURIColumnName);
            sqlfInsertInto.append(" = ");
            appendParameterOrVariable(sqlfInsertInto, d, useVariables, objecturiParameter, parameterToVariable);
            sqlfInsertInto.append(";\n");
        }

        //
        // ObjectProperty
        //

        if (null != properties)
        {
            Set<String> skip = ((UpdateableTableInfo)table).skipProperties();
            if (null != skip)
                done.addAll(skip);

            for (DomainProperty dp : properties)
            {
                // ignore property that 'wraps' a hard column
                if (done.contains(dp.getName()))
                    continue;
                // CONSIDER: IF (p IS NOT NULL) THEN ...
                sqlfObjectProperty.append("INSERT INTO exp.ObjectProperty (objectid, propertyid, typetag, mvindicator, ");
                PropertyType propertyType = dp.getPropertyDescriptor().getPropertyType();
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
                Parameter mv = new Parameter(dp.getName()+ MvColumn.MV_INDICATOR_SUFFIX, dp.getPropertyURI() + MvColumn.MV_INDICATOR_SUFFIX, null, JdbcType.VARCHAR);
                sqlfObjectProperty.append(",");
                appendParameterOrVariable(sqlfObjectProperty, d,useVariables, mv, parameterToVariable);
                Parameter v = new Parameter(dp.getName(), dp.getPropertyURI(), null, propertyType.getJdbcType());
                sqlfObjectProperty.append(",");
                appendParameterOrVariable(sqlfObjectProperty, d,useVariables, v, parameterToVariable);
                sqlfObjectProperty.append(");\n");
            }
        }

        //
        // PREPARE
        //

        Parameter.ParameterMap ret;

        if (!useVariables)
        {
            SQLFragment script = new SQLFragment();
            script.append(sqlfDeclare);
            script.append(sqlfObject);
            script.append(sqlfInsertInto);
            script.append(sqlfObjectProperty);
            if (null != sqlfSelectIds)
                script.append(sqlfSelectIds);
            ret = new Parameter.ParameterMap(conn, script, updatable.remapSchemaColumns());
        }
        else
        {
            // wrap in a function
            SQLFragment fn = new SQLFragment();
            String fnName = d.getGlobalTempTablePrefix() + "fn_" + GUID.makeHash();
            fn.append("CREATE FUNCTION ").append(fnName).append("(");
            // TODO d.execute() doesn't handle temp schema
            SQLFragment call = new SQLFragment("SELECT ");
            if (countReturnIds > 0)
                call.append("* FROM ");
            call.append(fnName).append("(");
            final SQLFragment drop = new SQLFragment("DROP FUNCTION " + fnName + "(");
            comma = "";
            for (Map.Entry<Parameter,String> e : parameterToVariable.entrySet())
            {
                Parameter p = e.getKey();
                String variable = e.getValue();
                String type = d.sqlTypeNameFromSqlType(p.getType().sqlType);
                fn.append("\n").append(comma);
                fn.append(variable);
                fn.append(" ");
                fn.append(type);
                fn.append(" -- ").append(p.getName());
                drop.append(comma).append(type);
                call.append(comma).append("?");
                call.add(p);
                comma = ",";
            }
            fn.append("\n) RETURNS ");
            if (countReturnIds>0)
                fn.append("SETOF RECORD");
            else
                fn.append("void");
            fn.append(" AS $$\n");
            drop.append(");");
            call.append(")");
            if (countReturnIds > 0)
            {
                if (countReturnIds == 1)
                    call.append(" AS x(A int)");
                else
                    call.append(" AS x(A int, B int)");
            }
            call.append(";");
            fn.append(sqlfDeclare);
            fn.append("BEGIN\n");
            fn.append(sqlfObject);
            fn.append(sqlfInsertInto);
            fn.append(sqlfObjectProperty);
            if (null != sqlfSelectIds)
            {
                fn.append("\nRETURN QUERY ");
                fn.append(sqlfSelectIds);
            }
            fn.append("\nEND;\n$$ LANGUAGE plpgsql;\n");

            Table.execute(table.getSchema(), fn);
            ret = new Parameter.ParameterMap(conn, call, updatable.remapSchemaColumns());
            ret.onClose(new Runnable() { @Override public void run()
            {
                try
                {
                    Table.execute(ExperimentService.get().getSchema(),drop);
                }
                catch (SQLException x)
                {
                    Logger.getLogger(Table.class).error("Error dropping temp function", x);
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

        ret.setRowIdIndex(selectRowIdIndex);
        ret.setObjectIdIndex(selectObjectIdIndex);

        return ret;
    }
}
