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
import org.labkey.api.query.AliasManager;
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

    // same as appendParameterOrVariable(), but with cast handling
    public static SQLFragment appendPropertyValue(SQLFragment f, SqlDialect d, DomainProperty dp, boolean useVariable, Parameter p, Map<Parameter, String> names)
    {
        String value;
        if (!useVariable)
        {
            value = "?";
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
            value = v;
        }
        if (JdbcType.valueOf(dp.getSqlType()) == JdbcType.BOOLEAN)
        {
            value = "CASE CAST(" + value + " AS "+ d.getBooleanDataType() + ")" +
                " WHEN " + d.getBooleanTRUE() + " THEN 1.0 " +
                " WHEN " + d.getBooleanFALSE() + " THEN 0.0" +
                " ELSE NULL END";
        }
        f.append(value);
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
    public static Parameter.ParameterMap insertStatement(Connection conn, TableInfo table, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
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
        String ifTHEN = d.isSqlServer() ? " BEGIN " : " THEN ";
        String ifEND = d.isSqlServer() ? " END " : "; END IF ";
        String containerIdConstant = null==c ? null : "'" + c.getId() + "'";

        // helper for generating procedure/function variation
        Map<Parameter, String> parameterToVariable = new IdentityHashMap<Parameter, String>();

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
                if (!d.isPostgreSQL() && !d.isSqlServer())
                    throw new IllegalStateException("Domains are only supported for sql server and postgres");

                objectIdVar = d.isPostgreSQL() ? "_$objectid$_" : "@_objectid_";

                useVariables = d.isPostgreSQL();
                sqlfDeclare.append("DECLARE ").append(objectIdVar).append(" INT");

                if (null == c)
                    containerParameter = new Parameter("container", JdbcType.VARCHAR);
//                if (autoFillDefaultColumns && null != c)
//                    containerParameter.setValue(c.getId(), true);

                // Insert a new row in exp.Object if there isn't already a row for this object

                // In the update case, it's still possible that there isn't a row in exp.Object - there might have been
                // no properties in the domain when the row was originally inserted
                sqlfInsertObject.append("INSERT INTO exp.Object (container, objecturi) ");
                sqlfInsertObject.append("SELECT ");
                if (null!=containerIdConstant)
                    sqlfInsertObject.append(containerIdConstant);
                else
                    appendParameterOrVariable(sqlfInsertObject, d, useVariables, containerParameter, parameterToVariable);
                sqlfInsertObject.append(" AS Container,");
                appendParameterOrVariable(sqlfInsertObject, d, useVariables, objecturiParameter, parameterToVariable);
                sqlfInsertObject.append(" AS ObjectURI WHERE NOT EXISTS (SELECT ObjectURI FROM exp.Object WHERE Container = ");
                if (null!=containerIdConstant)
                    sqlfInsertObject.append(containerIdConstant);
                else
                    appendParameterOrVariable(sqlfInsertObject, d, useVariables, containerParameter, parameterToVariable);
                sqlfInsertObject.append(" AND ObjectURI = ");
                appendParameterOrVariable(sqlfInsertObject, d, useVariables, objecturiParameter, parameterToVariable);
                sqlfInsertObject.append(")");

                // Grab the object's ObjectId
                sqlfSelectObject.append(setKeyword).append(objectIdVar).append(" = (");
                sqlfSelectObject.append("SELECT ObjectId FROM exp.Object WHERE Container = ");
                if (null!=containerIdConstant)
                    sqlfSelectObject.append(containerIdConstant);
                else
                    appendParameterOrVariable(sqlfSelectObject, d, useVariables, containerParameter, parameterToVariable);
                sqlfSelectObject.append(" AND ObjectURI = ");
                appendParameterOrVariable(sqlfSelectObject, d, useVariables, objecturiParameter, parameterToVariable);
                sqlfSelectObject.append(")");

                if (!insert)
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

        List<SQLFragment> cols = new ArrayList<SQLFragment>();
        List<SQLFragment> values = new ArrayList<SQLFragment>();
        ColumnInfo col = table.getColumn("Container");

        if (null != col && null != user)
        {
            cols.add(new SQLFragment("Container"));
            if (null == containerParameter && null == c)
                containerParameter = new Parameter("container", JdbcType.VARCHAR);
            if (null!=containerIdConstant)
                values.add(new SQLFragment(containerIdConstant));
            else
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
        boolean selectAutoIncrement = false;
        Integer selectObjectIdIndex = null;
        int countReturnIds = 0;    // TODO: Change to a boolean, selectObjectId?

        if (selectIds && null != objectIdVar)
        {
            sqlfSelectIds = new SQLFragment("SELECT ");
            sqlfSelectIds.append(objectIdVar);
            selectObjectIdIndex = ++countReturnIds;
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

            sqlfInsertInto.append(")");

            if (selectIds && null != autoIncrementColumn)
            {
                d.appendSelectAutoIncrement(sqlfInsertInto, table, autoIncrementColumn.getName());
                selectAutoIncrement = true;
            }
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
                Parameter v = new Parameter(dp.getName(), dp.getPropertyURI(), null, propertyType.getJdbcType());
                Parameter mv = new Parameter(dp.getName()+ MvColumn.MV_INDICATOR_SUFFIX, dp.getPropertyURI() + MvColumn.MV_INDICATOR_SUFFIX, null, JdbcType.VARCHAR);
                sqlfObjectProperty.append(stmtSep);
                stmtSep = ";\n";
                sqlfObjectProperty.append("IF (");
                appendPropertyValue(sqlfObjectProperty, d, dp, useVariables, v, parameterToVariable);
                sqlfObjectProperty.append(" IS NOT NULL OR ");
                appendParameterOrVariable(sqlfObjectProperty, d, useVariables, mv, parameterToVariable);
                sqlfObjectProperty.append(" IS NOT NULL)").append(ifTHEN);
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
                appendParameterOrVariable(sqlfObjectProperty, d, useVariables, mv, parameterToVariable);
                sqlfObjectProperty.append(",");
                appendPropertyValue(sqlfObjectProperty, d, dp, useVariables, v, parameterToVariable);
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
            script.appendStatement(sqlfDeclare, d);
            script.appendStatement(sqlfInsertObject, d);
            script.appendStatement(sqlfSelectObject, d);
            script.appendStatement(sqlfDelete, d);
            script.appendStatement(sqlfInsertInto, d);
            script.appendStatement(sqlfObjectProperty, d);
            script.appendStatement(sqlfSelectIds, d);

            ret = new Parameter.ParameterMap(table.getSchema().getScope(), conn, script, updatable.remapSchemaColumns());
        }
        else
        {
            // wrap in a function
            SQLFragment fn = new SQLFragment();
            String fnName = d.getGlobalTempTablePrefix() + "fn_" + GUID.makeHash();
            fn.append("CREATE FUNCTION ").append(fnName).append("(");
            // TODO d.execute() doesn't handle temp schema
            SQLFragment call = new SQLFragment();
            call.append(fnName).append("(");
            final SQLFragment drop = new SQLFragment("DROP FUNCTION " + fnName + "(");
            comma = "";
            for (Map.Entry<Parameter, String> e : parameterToVariable.entrySet())
            {
                Parameter p = e.getKey();
                String variable = e.getValue();
                String type = d.sqlTypeNameFromSqlType(p.getType().sqlType);
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
            if (countReturnIds>0)
                fn.append("SETOF RECORD");
            else
                fn.append("void");
            fn.append(" AS $$\n");
            drop.append(");");
            call.append(")");


            if (countReturnIds > 0)
            {
                call.insert(0, "SELECT * FROM ");
                if (countReturnIds == 1)
                    call.append(" AS x(A int)");
                else
                    call.append(" AS x(A int, B int)");
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

            fn.appendStatement(sqlfInsertObject, d);
            fn.appendStatement(sqlfSelectObject, d);
            fn.appendStatement(sqlfDelete, d);
            fn.appendStatement(sqlfInsertInto, d);
            fn.appendStatement(sqlfObjectProperty, d);
            if (null == sqlfSelectIds)
            {
                fn.appendStatement(new SQLFragment("RETURN"), d);
            }
            else
            {
                sqlfSelectIds.insert(0, "RETURN QUERY ");
                fn.appendStatement(sqlfSelectIds, d);
            }
            fn.append(";\nEND;\n$$ LANGUAGE plpgsql;\n");
            Table.execute(table.getSchema(), fn);
            ret = new Parameter.ParameterMap(table.getSchema().getScope(), conn, call, updatable.remapSchemaColumns());
            ret.onClose(new Runnable() { @Override public void run()
            {
                try
                {
                    Table.execute(ExperimentService.get().getSchema(), drop);
                }
                catch (SQLException x)
                {
                    Logger.getLogger(Table.class).error("Error dropping temp function.  SQLSTATE:" + x.getSQLState(), x);
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

        ret.setSelectRowId(selectAutoIncrement);
        ret.setObjectIdIndex(selectObjectIdIndex);

        return ret;
    }
}
