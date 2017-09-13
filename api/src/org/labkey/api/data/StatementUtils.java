/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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
    private static final Logger _log = Logger.getLogger(StatementUtils.class);

    public enum Operation {insert, update, merge}
    public static String OWNEROBJECTID = "exp$object$ownerobjectid";

    // configuration parameters
    private Operation _operation = Operation.insert;
    private SqlDialect _dialect;
    private TableInfo _table;
    private Set<String> _keyColumnNames = null;       // override the primary key of _table
    private Set<String> _skipColumnNames = null;
    private Set<String> _dontUpdateColumnNames = new HashSet<>();
    private boolean _updateBuiltInColumns = false;      // default to false, this should usually be handled by StandardDataIteratorBuilder
    private boolean _selectIds = false;
    private boolean _allowUpdateAutoIncrement = false;
    private boolean _allowInsertByLookupDisplayValue = false;

    // variable/parameter tracking helpers
    private boolean useVariables = false;
    private final Map<String, Object> _constants = new CaseInsensitiveHashMap<>();
    final Map<String, ParameterHolder> parameters = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());

    //
    // builder style methods
    //

    private StatementUtils(@NotNull Operation op, @NotNull TableInfo table)
    {
        _operation = op;
        _dialect = table.getSqlDialect();
        _table = table;
    }

    public StatementUtils dialect(SqlDialect dialect)
    {
        _dialect = dialect;
        return this;
    }

    public StatementUtils operation(@NotNull Operation op)
    {
        _operation = op;
        return this;
    }

    public StatementUtils constants(@NotNull Map<String,Object> constants)
    {
        _constants.putAll(constants);
        return this;
    }

    public StatementUtils keys(Set<String> keyNames)
    {
        _keyColumnNames = keyNames;
        return this;
    }

    public StatementUtils skip(Set<String> skip)
    {
        _skipColumnNames = skip;
        return this;
    }

    private StatementUtils noupdate(Set<String> noupdate)
    {
        _dontUpdateColumnNames = noupdate;
        return this;
    }

    private StatementUtils updateBuiltinColumns(boolean b)
    {
        _updateBuiltInColumns = b;
        return this;
    }

    private StatementUtils selectIds(boolean b)
    {
        _selectIds = b;
        return this;
    }

    private StatementUtils allowSetAutoIncrement(boolean b)
    {
        _allowUpdateAutoIncrement = b;
        return this;
    }

    public StatementUtils allowInsertByLookupDisplayValue(boolean b)
    {
        _allowInsertByLookupDisplayValue = b;
        return this;
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
        return new StatementUtils(Operation.insert, table)
            .updateBuiltinColumns(autoFillDefaultColumns)
            .selectIds(selectIds)
            .createStatement(conn, c, user);
    }


    public static Parameter.ParameterMap insertStatement(
            Connection conn, TableInfo table, @Nullable Set<String> skipColumnNames,
            @Nullable Container c, @Nullable User user, @Nullable Map<String,Object> constants,
            boolean selectIds, boolean autoFillDefaultColumns, boolean supportsAutoIncrementKey) throws SQLException
    {
        StatementUtils utils = new StatementUtils(Operation.insert, table)
                .skip(skipColumnNames)
                .allowSetAutoIncrement(supportsAutoIncrementKey)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds);
        if (null != constants)
            utils.constants(constants);
        return utils.createStatement(conn, c, user);
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
        return new StatementUtils(Operation.update, table)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds)
                .createStatement(conn, c, user);
    }


    private static Parameter.ParameterMap mergeStatement(Connection conn, TableInfo table, @Nullable Set<String> skipColumnNames, @Nullable Set<String> dontUpdate, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return new StatementUtils(Operation.merge, table)
                .skip(skipColumnNames)
                .noupdate(dontUpdate)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds)
                .createStatement(conn, c, user);
    }


    public static Parameter.ParameterMap mergeStatement(Connection conn, TableInfo table, @Nullable Set<String> keyNames, @Nullable Set<String> skipColumnNames, @Nullable Set<String> dontUpdate, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns, boolean supportsAutoIncrementKey) throws SQLException
    {
        return new StatementUtils(Operation.merge, table)
                .keys(keyNames)
                .skip(skipColumnNames)
                .allowSetAutoIncrement(supportsAutoIncrementKey)
                .noupdate(dontUpdate)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds)
                .createStatement(conn, c, user);
    }



    /*
     * Parameter and Variable helpers
     */

    private static class ParameterHolder
    {
        Parameter p;
        int length = -1;
        String variableName = null;
        Object constantValue = null;
        boolean isConstant = false;
    }

    private final static String pgRowVarPrefix = "$1.";
    private String makeVariableName(String name)
    {
        return (_dialect.isSqlServer() ? "@p"  + (parameters.size()+1) : pgRowVarPrefix) + AliasManager.makeLegalName(name, null);
    }

    private String makePgRowTypeName(String variableName)
    {
        return StringUtils.substringAfter(variableName, pgRowVarPrefix);
    }

    private ParameterHolder createParameter(ColumnInfo c)
    {
        ParameterHolder ph = parameters.get(c.getName());
        if (null == ph)
        {
            ph = new ParameterHolder();
            ph.p = new Parameter(c, null);
            initParameterHolder(ph, c);
            parameters.put(c.getName(), ph);
        }
        return ph;
    }

    private void initParameterHolder(ParameterHolder ph, @Nullable ColumnInfo columnInfo)
    {
        String name = ph.p.getName();
        JdbcType type = ph.p.getType();
        assert null != type;
        if (_constants.containsKey(name))
        {
            try
            {
                Object value = Parameter.getValueToBind(_constants.get(name), type);
                if (null == value || value instanceof Number || value instanceof String || value instanceof java.util.Date)
                {
                    ph.isConstant = true;
                    ph.constantValue = value;
                }
            }
            catch (SQLException x)
            {
                //
            }
        }
        ph.variableName = makeVariableName(name);
        if (type.isText() && type!= JdbcType.GUID && (null != columnInfo && columnInfo.getScale() > 0))
            ph.length = columnInfo.getScale();
    }


    private ParameterHolder createParameter(String name, JdbcType type)
    {
        ParameterHolder ph = parameters.get(name);
        if (null == ph)
        {
            ph = new ParameterHolder();
            ph.p = new Parameter(name, type);
            initParameterHolder(ph, null);
            parameters.put(name, ph);
        }
        return ph;
    }


    private ParameterHolder createParameter(String name, String uri, JdbcType type)
    {
        ParameterHolder ph = parameters.get(name);
        if (null == ph)
        {
            ph = new ParameterHolder();
            ph.p = new Parameter(name, uri, null, type);
            initParameterHolder(ph, null);
            parameters.put(name, ph);
        }
        return ph;
    }


    private SQLFragment appendParameterOrVariable(SQLFragment f, ParameterHolder ph)
    {
        if (ph.isConstant)
        {
            toLiteral(f, ph.constantValue);
        }
        else if (useVariables)
        {
            f.append(ph.variableName);
        }
        else
        {
            f.append("?");
            f.add(ph.p);
        }
        return f;
    }


    private SQLFragment appendParameterOrVariable(SQLFragment f, ColumnInfo col)
    {
        ParameterHolder p = createParameter(col);
        return appendParameterOrVariable(f, p);
    }


    private SQLFragment appendPropertyValue(SQLFragment f, DomainProperty dp, ParameterHolder p)
    {
        if (JdbcType.valueOf(dp.getSqlType()) == JdbcType.BOOLEAN)
        {
            f.append("CASE CAST(");
            appendParameterOrVariable(f, p);
            f.append(" AS ").append(_dialect.getBooleanDataType()).append(")")
                    .append(" WHEN ").append(_dialect.getBooleanTRUE()).append(" THEN 1.0 ")
                    .append(" WHEN ").append(_dialect.getBooleanFALSE()).append(" THEN 0.0 ")
                    .append(" ELSE NULL END");
            return f;
        }
        else
        {
            return appendParameterOrVariable(f, p);
        }
    }



    private Parameter.ParameterMap createStatement(Connection conn, @Nullable Container c, User user) throws SQLException
    {
        if (!(_table instanceof UpdateableTableInfo))
            throw new IllegalArgumentException("Table must be an UpdateableTableInfo");

        UpdateableTableInfo updatable = (UpdateableTableInfo)_table;
        TableInfo table = updatable.getSchemaTableInfo();

        if (table.getTableType() != DatabaseTableType.TABLE || null == table.getMetaDataName())
            throw new IllegalArgumentException();

        if (Operation.merge == _operation)
        {
            if (!_dialect.isPostgreSQL() && !_dialect.isSqlServer())
                throw new IllegalArgumentException("Merge is only supported/tested on postgres and sql server");
        }

        useVariables = Operation.merge == _operation; //  && dialect.isPostgreSQL();
        String ifTHEN = _dialect.isSqlServer() ? " BEGIN " : " THEN ";
        String ifEND = _dialect.isSqlServer() ? " END " : " END IF ";

        if (null != c)
        {
            assert null == _constants.get("container") || c.getId().equals(_constants.get("container"));
            if (null == _constants.get("container"))
                _constants.put("container", c.getId());
        }

        String objectURIColumnName = updatable.getObjectUriType() == UpdateableTableInfo.ObjectUriType.schemaColumn
                ? updatable.getObjectURIColumnName()
                : "objecturi";
        ParameterHolder objecturiParameter = null;
        if (null != objectURIColumnName)
            objecturiParameter = createParameter(objectURIColumnName, JdbcType.VARCHAR);

        String comma;
        Set<String> done = Sets.newCaseInsensitiveHashSet();

        String objectIdVar = null;
        String objectURIVar = null;
        String rowIdVar = null;
        String setKeyword = _dialect.isPostgreSQL() ? "" : "SET ";

        //
        // Keys for UPDATE or MERGE
        //

        // TODO more control/configuration
        // using objectURIColumnName preferentially to be backward compatible with OntologyManager.saveTabDelimited
        //    which in turn is only called by LuminexDataHandler.saveDataRows()
        LinkedHashMap<FieldKey,ColumnInfo> keys = new LinkedHashMap<>();
        ColumnInfo col = table.getColumn("Container");
        if (null != col)
            keys.put(col.getFieldKey(), col);
        if (null != _keyColumnNames && !_keyColumnNames.isEmpty())
        {
            for (String name : _keyColumnNames)
            {
                col = table.getColumn(name);
                if (null == col)
                    throw new IllegalArgumentException("Column not found: " + name);
                keys.put(col.getFieldKey(), col);
            }
        }
        else
        {
            col = objectURIColumnName == null ? null : table.getColumn(objectURIColumnName);
            if (null != col)
                keys.put(col.getFieldKey(), col);
            else
            {
                for (ColumnInfo pk : _table.getPkColumns())
                    keys.put(pk.getFieldKey(), pk);
            }
        }

        //
        // exp.Objects INSERT
        //

        SQLFragment sqlfDeclare = new SQLFragment();
        SQLFragment sqlfPreselectObject = new SQLFragment();
        SQLFragment sqlfInsertObject = new SQLFragment();
        SQLFragment sqlfSelectObject = new SQLFragment();
        SQLFragment sqlfObjectProperty = new SQLFragment();
        SQLFragment sqlfDelete = new SQLFragment();

        Domain domain = _table.getDomain();
        DomainKind domainKind = _table.getDomainKind();
        List<? extends DomainProperty> properties = Collections.emptyList();

        boolean hasObjectURIColumn = objectURIColumnName != null && table.getColumn(objectURIColumnName) != null;
        if (hasObjectURIColumn)
            _dontUpdateColumnNames.add(objectURIColumnName);

        if (null != domain && null != domainKind && StringUtils.isEmpty(domainKind.getStorageSchemaName()))
        {
            properties = domain.getProperties();

            if (!properties.isEmpty())
            {
                if (!_dialect.isPostgreSQL() && !_dialect.isSqlServer())
                    throw new IllegalStateException("Domains are only supported for sql server and postgres");

                objectIdVar = _dialect.isPostgreSQL() ? "_$objectid$_" : "@_objectid_";
                sqlfDeclare.append("DECLARE ").append(objectIdVar).append(" INT;\n");
                objectURIVar = _dialect.isPostgreSQL() ? "_$objecturi$_" : "@_objecturi_";
                sqlfDeclare.append("DECLARE ").append(objectURIVar).append(" ").append(_dialect.sqlTypeNameFromJdbcType(JdbcType.VARCHAR)).append("(300);\n");
                useVariables = _dialect.isPostgreSQL();

                ParameterHolder containerParameter = createParameter("container", JdbcType.GUID);

                // Insert a new row in exp.Object if there isn't already a row for this object

                // Grab the object's ObjectId based on the pk of the base table
                if (hasObjectURIColumn)
                {
                    sqlfPreselectObject.append(setKeyword).append(objectURIVar).append(" = (");
                    sqlfPreselectObject.append("SELECT ").append(table.getColumn(objectURIColumnName).getSelectName());
                    sqlfPreselectObject.append(" FROM ").append(table.getSelectName());
                    sqlfPreselectObject.append(getPkWhereClause(keys));
                    sqlfPreselectObject.append(");\n");
                }

                SQLFragment sqlfWhereObjectURI = new SQLFragment();
                sqlfWhereObjectURI.append("((").append(objectURIVar).append(" IS NOT NULL AND ObjectURI = ").append(objectURIVar).append(")");
                sqlfWhereObjectURI.append(" OR ObjectURI = ");
                appendParameterOrVariable(sqlfWhereObjectURI, objecturiParameter);
                sqlfWhereObjectURI.append(")");

                // In the update case, it's still possible that there isn't a row in exp.Object - there might have been
                // no properties in the domain when the row was originally inserted
                sqlfInsertObject.append("INSERT INTO exp.Object (container, objecturi) ");
                sqlfInsertObject.append("SELECT ");
                appendParameterOrVariable(sqlfInsertObject, containerParameter);
                sqlfInsertObject.append(" AS Container,");
                appendParameterOrVariable(sqlfInsertObject, objecturiParameter);
                sqlfInsertObject.append(" AS ObjectURI WHERE NOT EXISTS (SELECT ObjectURI FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfInsertObject, containerParameter);
                sqlfInsertObject.append(" AND ").append(sqlfWhereObjectURI).append(");\n");

                // re-grab the object's ObjectId, in case it was just inserted
                sqlfSelectObject.append(setKeyword).append(objectIdVar).append(" = (");
                sqlfSelectObject.append("SELECT ObjectId FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfSelectObject, containerParameter);
                sqlfSelectObject.append(" AND ").append(sqlfWhereObjectURI).append(");\n");

                if (Operation.insert != _operation)
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
                    sqlfDelete.append(");\n");
                }
            }
        }

        //
        // BASE TABLE INSERT()
        //

        List<SQLFragment> cols = new ArrayList<>();
        List<SQLFragment> values = new ArrayList<>();

        if (_updateBuiltInColumns && Operation.update != _operation)
        {
            col = table.getColumn("Owner");
            if (null != col && null != user)
            {
                cols.add(new SQLFragment(col.getSelectName()));
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("Owner");
            }
            col = table.getColumn("CreatedBy");
            if (null != col && null != user)
            {
                cols.add(new SQLFragment(col.getSelectName()));
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("CreatedBy");
            }
            col = table.getColumn("Created");
            if (null != col)
            {
                cols.add(new SQLFragment(col.getSelectName()));
                values.add(new SQLFragment("CURRENT_TIMESTAMP"));   // Instead of {fn now()} -- see #27534
                done.add("Created");
            }
        }

        ColumnInfo colModifiedBy = table.getColumn("ModifiedBy");
        if (_updateBuiltInColumns && null != colModifiedBy && null != user)
        {
            cols.add(new SQLFragment(colModifiedBy.getSelectName()));
            values.add(new SQLFragment().append(user.getUserId()));
            done.add("ModifiedBy");
        }

        ColumnInfo colModified = table.getColumn("Modified");
        if (_updateBuiltInColumns && null != colModified)
        {
            cols.add(new SQLFragment(colModified.getSelectName()));
            values.add(new SQLFragment("CURRENT_TIMESTAMP"));   // Instead of {fn now()} -- see #27534
            done.add("Modified");
        }
        ColumnInfo colVersion = table.getVersionColumn();
        if (_updateBuiltInColumns && null != colVersion && !done.contains(colVersion.getName()))
        {
            SQLFragment expr = colVersion.getVersionUpdateExpression();
            if (null != expr)
            {
                cols.add(new SQLFragment(colVersion.getSelectName()));
                values.add(expr);
                done.add(colVersion.getName());
            }
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
            // if we're allowing the caller to set the auto-increment column, then treat like a regular column
            if (column.isAutoIncrement() && !_allowUpdateAutoIncrement)
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
                if (null != _skipColumnNames && _skipColumnNames.contains(StringUtils.defaultString(remap.get(name),name)))
                    continue;
                ParameterHolder ph = createParameter(column);
                appendParameterOrVariable(valueSQL, ph);
            }
            cols.add(new SQLFragment(column.getSelectName()));
            values.add(valueSQL);
        }

        SQLFragment sqlfSelectIds = null;
        boolean selectAutoIncrement = false;

        assert cols.size() == values.size() : cols.size() + " columns and " + values.size() + " values - should match";

        //
        // INSERT
        //

        SQLFragment sqlfInsertInto = new SQLFragment();
        if (Operation.insert == _operation || Operation.merge == _operation)
        {
            // Create a standard INSERT INTO table (col1, col2) VALUES (val1, val2) statement
            // or (for degenerate, empty values case) INSERT INTO table VALUES (DEFAULT)
            sqlfInsertInto.append("INSERT INTO ").append(table.getSelectName());

            if (values.isEmpty())
            {
                sqlfInsertInto.append("\nVALUES (DEFAULT)");
            }
            else
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

                sqlfInsertInto.append("\nSELECT ");
                comma = "";
                for (SQLFragment valueSQL : values)
                {
                    sqlfInsertInto.append(comma);
                    comma = ", ";
                    sqlfInsertInto.append(valueSQL);
                }
            }

            if (_selectIds && null != autoIncrementColumn)
            {
                selectAutoIncrement = true;
                if (null != objectIdVar)
                    rowIdVar = "_rowid_";
                rowIdVar = _dialect.addReselect(sqlfInsertInto, autoIncrementColumn, rowIdVar);
                if (null != objectIdVar)
                    sqlfDeclare.append("DECLARE ").append(rowIdVar).append(" INT;\n");  // TODO: Move this into addReselect()?
            }
        }

        //
        // UPDATE
        //

        SQLFragment sqlfUpdate = new SQLFragment();
        SQLFragment sqlfWherePK = getPkWhereClause(keys);

        if (Operation.update == _operation || Operation.merge == _operation)
        {
            // Create a standard UPDATE table SET col1 = val1, col2 = val2 statement
            sqlfUpdate.append("UPDATE ").append(table.getSelectName()).append("\nSET ");
            comma = "";
            int updateCount = 0;
            for (int i = 0; i < cols.size(); i++)
            {
                FieldKey fk = new FieldKey(null, cols.get(i).getSQL());
                if (keys.containsKey(fk) || null != _dontUpdateColumnNames && _dontUpdateColumnNames.contains(fk.getName()))
                    continue;
                sqlfUpdate.append(comma);
                comma = ", ";
                sqlfUpdate.append(cols.get(i));
                sqlfUpdate.append(" = ");
                sqlfUpdate.append(values.get(i));
                updateCount++;
            }
            sqlfUpdate.append(sqlfWherePK);
            sqlfUpdate.append(";\n");

            if (Operation.merge == _operation)
            {
                // updateCount can equal 0.  This happens particularly when inserting into junction tables where
                // there are two columns and both are in the primary key
                if (0 == updateCount)
                {
                    sqlfUpdate = new SQLFragment();
                    sqlfInsertInto.append("\nWHERE NOT EXISTS (SELECT * FROM ").append(table.getSelectName());
                    sqlfInsertInto.append(sqlfWherePK);
                    sqlfInsertInto.append(")");
                }
                else
                {
                    sqlfUpdate.append("IF ");
                    sqlfUpdate.append(_dialect.isSqlServer() ? "@@ROWCOUNT=0" : "NOT FOUND");
                    sqlfUpdate.append(ifTHEN).append("\n\t");

                    sqlfInsertInto.append(";\n");
                    sqlfInsertInto.append(ifEND);
                }
            }
        }

        if (Operation.insert == _operation || Operation.merge == _operation)
            sqlfInsertInto.append(";\n");

        if (_selectIds && (null != objectIdVar || null != rowIdVar))
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

        if (!properties.isEmpty())
        {
            Set<String> skip = updatable.skipProperties();
            if (null != skip)
                done.addAll(skip);

            for (DomainProperty dp : properties)
            {
                // ignore property that 'wraps' a hard column
                if (done.contains(dp.getName()))
                    continue;
                PropertyType propertyType = dp.getPropertyDescriptor().getPropertyType();
                ParameterHolder v = createParameter(dp.getName(), dp.getPropertyURI(), propertyType.getJdbcType());
                ParameterHolder mv = createParameter(dp.getName()+ MvColumn.MV_INDICATOR_SUFFIX, dp.getPropertyURI() + MvColumn.MV_INDICATOR_SUFFIX, JdbcType.VARCHAR);
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
                sqlfObjectProperty.append(");\n");
                sqlfObjectProperty.append(ifEND);
                sqlfObjectProperty.append(";\n");
            }
        }

        //
        // PREPARE
        //

        Parameter.ParameterMap ret;

        if (!useVariables)
        {
            SQLFragment script = new SQLFragment();
            Stream.of(sqlfDeclare, sqlfPreselectObject, sqlfInsertObject, sqlfSelectObject, sqlfDelete, sqlfUpdate, sqlfInsertInto, sqlfObjectProperty, sqlfSelectIds)
                .filter(f -> null != f && !f.isEmpty())
                .forEach(script::append);
            ret = new Parameter.ParameterMap(table.getSchema().getScope(), conn, script, remap);
        }
        else if (_dialect.isSqlServer())
        {
            if (!parameters.isEmpty())
            {
                SQLFragment select = new SQLFragment();
                sqlfDeclare.append("DECLARE ");
                select.append("SELECT ");
                comma = "";
                for (Map.Entry<String, ParameterHolder> e : parameters.entrySet())
                {
                    ParameterHolder ph = e.getValue();
                    sqlfDeclare.append(comma);
                    String variable = variableDeclaration(sqlfDeclare, ph);
                    select.append(comma).append(variable).append("=?");
                    select.add(ph.p);
                    comma = ", ";
                }
                sqlfDeclare.append(";\n");
                sqlfDeclare.append(select);
                sqlfDeclare.append(";\n");
            }
            SQLFragment script = new SQLFragment();
            Stream.of(sqlfDeclare, sqlfPreselectObject, sqlfInsertObject, sqlfSelectObject, sqlfDelete, sqlfUpdate, sqlfInsertInto, sqlfObjectProperty, sqlfSelectIds)
                .filter(f -> null != f && !f.isEmpty())
                .forEach(script::append);
            _log.debug(script.toDebugString());
            ret = new Parameter.ParameterMap(table.getSchema().getScope(), conn, script, remap);
        }
        else
        {
            // wrap in a function with a single ROW() constructor argument
            SQLFragment fn = new SQLFragment();
            String fnName = _dialect.getGlobalTempTablePrefix() + "fn_" + GUID.makeHash();
            String typeName = fnName + "type";
            fn.append("CREATE TYPE ").append(typeName).append(" AS (");
            // TODO d.execute() doesn't handle temp schema
            SQLFragment call = new SQLFragment();
            call.append(fnName).append("(ROW(");
            comma = "";
            for (Map.Entry<String, ParameterHolder> e : parameters.entrySet())
            {
                ParameterHolder ph = e.getValue();
                String type = _dialect.sqlTypeNameFromJdbcType(ph.p.getType());
                fn.append("\n").append(comma);
                fn.append(makePgRowTypeName(ph.variableName));
                fn.append(" ");
                fn.append(type);
                // For PG (29687) we need the length for CHAR type
                if (_dialect.isPostgreSQL() && JdbcType.CHAR.equals(ph.p.getType()))
                    fn.append("(").append(ph.length).append(")");
                call.append(comma).append("?");
                call.add(ph.p);
                comma = ",";
            }
            fn.append("\n);\n");
            fn.append("CREATE FUNCTION ").append(fnName).append("(").append(typeName).append(") ");
            fn.append("RETURNS ");
            if (null != sqlfSelectIds)
                fn.append("SETOF RECORD");
            else
                fn.append("void");
            fn.append(" AS $$\n");
            call.append("))");


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

            fn.append(sqlfDeclare);

            fn.append("BEGIN\n");
            fn.append("-- ").append(_operation.name()).append("\n");
            Stream.of(sqlfPreselectObject, sqlfInsertObject, sqlfSelectObject, sqlfDelete, sqlfUpdate, sqlfInsertInto, sqlfObjectProperty)
                .filter(f -> null != f && !f.isEmpty())
                .forEach(fn::append);
            if (null == sqlfSelectIds)
            {
                fn.append(new SQLFragment("RETURN;\n"));
            }
            else
            {
                sqlfSelectIds.insert(0, "RETURN QUERY\n");
                fn.append(sqlfSelectIds);
                fn.append(";\n");
            }
            fn.append("END;\n$$ LANGUAGE plpgsql;\n");
            _log.debug(fn.toDebugString());
            final SQLFragment drop = new SQLFragment("DROP TYPE IF EXISTS ").append(typeName).append(" CASCADE;");
            _log.debug(drop.toDebugString());
            new SqlExecutor(table.getSchema()).execute(fn);
            ret = new Parameter.ParameterMap(table.getSchema().getScope(), conn, call, updatable.remapSchemaColumns());
            ret.setDebugSql(fn.getSQL() + "--\n" + call.toDebugString());
            ret.onClose(() -> {
                try
                {
                    new SqlExecutor(ExperimentService.get().getSchema()).execute(drop);
                }
                catch (Exception x)
                {
                    _log.error("Error dropping custom rowtype for temp function.", x);
                }
            });
        }

        if (_selectIds)
        {
            // Why is one of these boolean and the other an index?? I don't know
            ret.setSelectRowId(selectAutoIncrement);
            if (null != objectIdVar)
                ret.setObjectIdIndex(selectAutoIncrement ? 2 : 1);
        }

        return ret;
    }

    private SQLFragment getPkWhereClause(LinkedHashMap<FieldKey,ColumnInfo> keys)
    {
        SQLFragment sqlfWherePK = new SQLFragment();
        sqlfWherePK.append("\nWHERE ");
        String and = "";
        for (Map.Entry<FieldKey, ColumnInfo> e : keys.entrySet())
        {
            ColumnInfo keyCol = e.getValue();
            ParameterHolder keyColPh = createParameter(keyCol);

            sqlfWherePK.append(and);
            sqlfWherePK.append("(");
            sqlfWherePK.append(keyCol.getSelectName());
            sqlfWherePK.append(" = ");
            appendParameterOrVariable(sqlfWherePK, keyColPh);
            if (keyCol.isNullable())
            {
                sqlfWherePK.append(" OR ");
                sqlfWherePK.append(keyCol.getSelectName());
                sqlfWherePK.append(" IS NULL AND ");
                appendParameterOrVariable(sqlfWherePK, keyColPh);
                sqlfWherePK.append(" IS NULL");
            }
            sqlfWherePK.append(")");
            and = " AND ";
        }
        return sqlfWherePK;
    }

    private String variableDeclaration(SQLFragment sqlfDeclare, ParameterHolder ph)
    {
        String variable = ph.variableName;
        sqlfDeclare.append(variable);
        sqlfDeclare.append(" ");
        JdbcType jdbcType = ph.p.getType();
        assert null != jdbcType;
        String type = _dialect.sqlTypeNameFromJdbcType(jdbcType);
        assert null != type;
        // Workaround - SQLServer doesn't support TEXT, NTEXT, or IMAGE as local variables in statements, but is OK with NVARCHAR(MAX)
        if (("NTEXT".equalsIgnoreCase(type) || "TEXT".equalsIgnoreCase(type)) && _dialect.isSqlServer())
        {
            type = "NVARCHAR(MAX)";
        }
        sqlfDeclare.append(type);
        if (jdbcType.isText() && ph.p.getType() != JdbcType.LONGVARCHAR && ph.p.getType() != JdbcType.GUID)
        {
            int length = ph.length > 0 ? ph.length : 4000;
            sqlfDeclare.append("(").append(length).append(")");
        }
        return variable;
    }


    private void toLiteral(SQLFragment f, Object value)
    {
        if (null == value)
        {
            f.append("NULL");
            return;
        }
        if (value instanceof Number)
        {
            f.append(value.toString());
            return;
        }
        if (value instanceof SimpleTranslator.NowTimestamp)
        {
            f.append("CURRENT_TIMESTAMP");   // Instead of {fn now()} -- see #27534
            return;
        }
        if (value instanceof java.sql.Date)
        {
            f.append("{d '").append(DateUtil.formatDateISO8601((java.sql.Date)value)).append("'}");
            return;
        }
        else if (value instanceof java.util.Date)
        {
            f.append("{ts '").append(DateUtil.formatDateTimeISO8601((java.util.Date)value)).append("'}");
            return;
        }
        assert value instanceof String;
        value = String.valueOf(value);
        f.append(_dialect.getStringHandler().quoteStringLiteral((String) value));
    }


    public static class TestCase extends Assert
    {
        TableInfo principals;
        TableInfo test;
        User user;
        Container container;

        void init()
        {
            principals = DbSchema.get("core", DbSchemaType.Module).getTable("principals");
            test = DbSchema.get("test", DbSchemaType.Module).getTable("testtable2");
            container = JunitUtil.getTestContainer();
            user = TestContext.get().getUser();
        }


        @Test
        public void testInsert() throws Exception
        {
            init();
            Parameter.ParameterMap m = null;
            try (Connection conn = principals.getSchema().getScope().getConnection())
            {
                m = StatementUtils.insertStatement(conn, principals, null, container, user, null, true, true, false);
                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;

                m = StatementUtils.insertStatement(conn, test, null, container, user, null, true, true, false);
                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }


        @Test
        public void testUpdate() throws Exception
        {
            init();
            Parameter.ParameterMap m = null;
            try (Connection conn = principals.getSchema().getScope().getConnection())
            {
                m = StatementUtils.updateStatement(conn, principals, container, user, true, true);
                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;

                m = StatementUtils.updateStatement(conn, test, container, user, true, true);
                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }


        @Test
        public void testMerge() throws Exception
        {
            init();
            Parameter.ParameterMap m = null;
            try (Connection conn = principals.getSchema().getScope().getConnection())
            {
                m = StatementUtils.mergeStatement(conn, principals, null, null, container, user, false, true);
                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;

                m = StatementUtils.mergeStatement(conn, test, null, null, container, user, false, true);
                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }
    }
}
