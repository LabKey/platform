/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
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
import org.labkey.api.util.logging.LogHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final Logger _log = LogHelper.getLogger(StatementUtils.class, "SQL insert/update/delete generation");

    public enum Operation {insert, update, merge}
    public static String OWNEROBJECTID = "exp$object$ownerobjectid";

    // configuration parameters
    private Operation _operation = Operation.insert;
    private SqlDialect _dialect;
    private final TableInfo _targetTable;
    private Set<String> _keyColumnNames = null;       // override the primary key of _table
    private Set<String> _skipColumnNames = Set.of();
    private final Set<String> _dontUpdateColumnNames = new CaseInsensitiveHashSet();
    private boolean _updateBuiltInColumns = false;      // default to false, this should usually be handled by StandardDataIteratorBuilder
    private boolean _selectIds = false;
    private boolean _selectObjectUri = false;
    private boolean _allowUpdateAutoIncrement = false;

    // variable/parameter tracking helpers
    private boolean useVariables = false;
    private final Map<String, Object> _constants = new CaseInsensitiveHashMap<>();
    final Map<String, ParameterHolder> parameters = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());

    //
    // builder style methods
    //

    //Vocabulary adhoc properties
    private Set<DomainProperty> _vocabularyProperties = new HashSet<>();

    public StatementUtils(@NotNull Operation op, @NotNull TableInfo table)
    {
        _operation = op;
        _dialect = table.getSqlDialect();
        _targetTable = table;
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
        _skipColumnNames = null==skip ? Set.of() : skip;
        return this;
    }

    public StatementUtils noupdate(Set<String> noupdate)
    {
        if (null != noupdate)
            _dontUpdateColumnNames.addAll(noupdate);
        return this;
    }

    public StatementUtils updateBuiltinColumns(boolean b)
    {
        _updateBuiltInColumns = b;
        return this;
    }

    public StatementUtils selectIds(boolean b)
    {
        _selectIds = b;
        return this;
    }

    public StatementUtils selectObjectUri(boolean b)
    {
        _selectObjectUri = b;
        return this;
    }

    public StatementUtils allowSetAutoIncrement(boolean b)
    {
        _allowUpdateAutoIncrement = b;
        return this;
    }

    public StatementUtils setVocabularyProperties(Set<DomainProperty> vocabularyProperties)
    {
        _vocabularyProperties = vocabularyProperties;
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
    public static ParameterMapStatement insertStatement(Connection conn, TableInfo table, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return new StatementUtils(Operation.insert, table)
            .updateBuiltinColumns(autoFillDefaultColumns)
            .selectIds(selectIds)
            .createStatement(conn, c, user);
    }


    public static ParameterMapStatement insertStatement(
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
    public static ParameterMapStatement updateStatement(Connection conn, TableInfo table, @Nullable Container c, User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return new StatementUtils(Operation.update, table)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds)
                .createStatement(conn, c, user);
    }


    private static ParameterMapStatement mergeStatement(Connection conn, TableInfo table, @Nullable Set<String> skipColumnNames, @Nullable Set<String> dontUpdate, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return new StatementUtils(Operation.merge, table)
                .skip(skipColumnNames)
                .noupdate(dontUpdate)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds)
                .createStatement(conn, c, user);
    }


    public static ParameterMapStatement mergeStatement(Connection conn, TableInfo table, @Nullable Set<String> keyNames, @Nullable Set<String> skipColumnNames, @Nullable Set<String> dontUpdate, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns, boolean supportsAutoIncrementKey) throws SQLException
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
        ParameterHolder(Parameter p)
        {
            this.p = p;
            _columnInfo = null;
        }

        ParameterHolder(Parameter p, ColumnInfo c)
        {
            this.p = p;
            _columnInfo = c;
        }

        int getScale()
        {
            var type = Objects.requireNonNull(p.getType());
            if (type.isText() && type!= JdbcType.GUID && (null != _columnInfo && _columnInfo.getScale() > 0))
                return _columnInfo.getScale();
            return -1;
        }

        int getPrecision()
        {
            return null==_columnInfo ? -1 : _columnInfo.getPrecision();
        }

        final Parameter p;
        final ColumnInfo _columnInfo;
        String variableName = null;
        Object constantValue = null;
        boolean isConstant = false;
    }

    private final static String pgRowVarPrefix = "$1.";
    private String makeVariableName(String name)
    {
        return (_dialect.isSqlServer() ? "@p"  + (parameters.size()+1) : pgRowVarPrefix) + AliasManager.makeLegalName(name, _dialect);
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
            ph = new ParameterHolder(new Parameter(c.getName(), c.getPropertyURI(), null, c.getJdbcType()), c);
            // NOTE: earlier DataIterator should probably split file into two columns: attachment_name, attachment_body
            if (c.getInputType().equalsIgnoreCase("file") && c.getJdbcType() == JdbcType.VARCHAR)
                ph.p.setFileAsName(true);
            initParameterHolder(ph);
            parameters.put(c.getName(), ph);
        }
        return ph;
    }

    private void initParameterHolder(ParameterHolder ph)
    {
        String name = ph.p.getName();
        JdbcType type = ph.p.getType();
        assert null != type;
        if (_constants.containsKey(name))
        {
            Object value = Parameter.getValueToBind(_constants.get(name), type);
            if (null == value || value instanceof Number || value instanceof String || value instanceof java.util.Date)
            {
                ph.isConstant = true;
                ph.constantValue = value;
            }
        }
        ph.variableName = makeVariableName(name);
    }


    private ParameterHolder createParameter(String name, JdbcType type)
    {
        ParameterHolder ph = parameters.get(name);
        if (null == ph)
        {
            ph = new ParameterHolder(new Parameter(name, type));
            initParameterHolder(ph);
            parameters.put(name, ph);
        }
        return ph;
    }


    private ParameterHolder createParameter(String name, String uri, JdbcType type)
    {
        ParameterHolder ph = parameters.get(name);
        if (null == ph)
        {
            ph = new ParameterHolder(new Parameter(name, uri, null, type));
            initParameterHolder(ph);
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


    private SQLFragment appendPropertyValue(SQLFragment f, DomainProperty dp, ParameterHolder p)
    {
        if (dp.getJdbcType() == JdbcType.BOOLEAN)
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

    private void appendSQLFObjectProperty(SQLFragment sqlfObjectProperty, DomainProperty dp, String objectIdVar, String ifTHEN, String ifEND)
    {
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
        sqlfObjectProperty.append(propertyType.getValueTypeColumn());
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

    private void appendSQLFDeleteObjectProperty(SQLFragment sqlfDelete, String objectIdVar, List<? extends DomainProperty> domainProperties, Set<DomainProperty> vocabularyProperties)
    {
        var properties = null == domainProperties ? vocabularyProperties : domainProperties;
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

    public void setObjectUriPreselect(SQLFragment sqlfPreselectObject, TableInfo table, LinkedHashMap<FieldKey,ColumnInfo> keys, String objectURIVar, String objectURIColumnName, ParameterHolder objecturiParameter)
    {
        String setKeyword = _dialect.isPostgreSQL() ? "" : "SET ";
        if (Operation.merge == _operation)
        {
            // this seems overkill actually, but I'm focused on optimizing insert right now (MAB)
            sqlfPreselectObject.append(setKeyword).append(objectURIVar).append(" = COALESCE((");
            sqlfPreselectObject.append("SELECT ").append(table.getColumn(objectURIColumnName).getSelectName());
            sqlfPreselectObject.append(" FROM ").append(table.getSelectName());
            sqlfPreselectObject.append(getPkWhereClause(keys));
            sqlfPreselectObject.append("),");
            appendParameterOrVariable(sqlfPreselectObject, objecturiParameter);
            sqlfPreselectObject.append(");\n");

        }
        else
        {
            sqlfPreselectObject.append(setKeyword).append(objectURIVar).append(" = ");
            appendParameterOrVariable(sqlfPreselectObject, objecturiParameter);
            sqlfPreselectObject.append(";\n");
        }
    }

    public ParameterMapStatement createStatement(Connection conn, @Nullable Container c, User user) throws SQLException
    {
        if (!(_targetTable instanceof UpdateableTableInfo))
            throw new IllegalArgumentException("Table must be an UpdateableTableInfo");

        UpdateableTableInfo updatable = (UpdateableTableInfo) _targetTable;
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
                // see 26661 and 41053
                // NOTE: IMO we should not be using updatable.getPkColumns() here! If the caller doesn't want to use the
                // 'real' PK from the SchemaTableInfo for update/merge, then the alternate keys should be explicitly specified
                // using StatementUtils.keys()
                for (String pkName : updatable.getPkColumnNames())
                {
                    col = table.getColumn(pkName);
                    if (null == col)
                        throw new IllegalStateException("pk column not found: " + pkName);
                    keys.put(col.getFieldKey(), col);
                }
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

        Domain domain = updatable.getDomain();
        DomainKind domainKind = updatable.getDomainKind();
        List<? extends DomainProperty> properties = Collections.emptyList();

        boolean hasObjectURIColumn = objectURIColumnName != null && table.getColumn(objectURIColumnName) != null;
        boolean alwaysInsertExpObject = hasObjectURIColumn && updatable.isAlwaysInsertExpObject();
        if (hasObjectURIColumn)
            _dontUpdateColumnNames.add(objectURIColumnName);
// TODO Should we add created and createdby? Or make the caller decide?
//        _dontUpdateColumnNames.add("Created");
//        _dontUpdateColumnNames.add("CreatedBy");

        boolean objectUriPreselectSet = false;
        boolean isMaterializedDomain = null != domain && null != domainKind && StringUtils.isNotEmpty(domainKind.getStorageSchemaName());
        if (alwaysInsertExpObject || (null != domain && !isMaterializedDomain) || !_vocabularyProperties.isEmpty())
        {
            properties = (null==domain||isMaterializedDomain) ? Collections.emptyList() : domain.getProperties();

            if (alwaysInsertExpObject || !properties.isEmpty() || !_vocabularyProperties.isEmpty())
            {
                if (!_dialect.isPostgreSQL() && !_dialect.isSqlServer())
                    throw new IllegalStateException("Domains are only supported for sql server and postgres");

                objectIdVar = _dialect.isPostgreSQL() ? "_$objectid$_" : "@_objectid_";
                sqlfDeclare.append("DECLARE ").append(objectIdVar).append(" INT;\n");
                objectURIVar = _dialect.isPostgreSQL() ? "_$objecturi$_" : "@_objecturi_";
                sqlfDeclare.append("DECLARE ").append(objectURIVar).append(" ").append(_dialect.getSqlTypeName(JdbcType.VARCHAR)).append("(300);\n");
                useVariables = _dialect.isPostgreSQL();

                ParameterHolder containerParameter = createParameter("container", JdbcType.GUID);

                // Insert a new row in exp.Object if there isn't already a row for this object

                // Grab the object's ObjectId based on the pk of the base table
                if (hasObjectURIColumn || !_vocabularyProperties.isEmpty())
                {
                    setObjectUriPreselect(sqlfPreselectObject, table, keys, objectURIVar, objectURIColumnName, objecturiParameter);
                    objectUriPreselectSet = true;
                }

                SQLFragment sqlfWhereObjectURI = new SQLFragment();
                sqlfWhereObjectURI.append("(ObjectURI = ").append(objectURIVar).append(")");

                // In the update case, it's still possible that there isn't a row in exp.Object - there might have been
                // no properties in the domain when the row was originally inserted
                sqlfInsertObject.append("INSERT INTO exp.Object (container, objecturi, ownerobjectid) ");
                sqlfInsertObject.append("SELECT ");
                appendParameterOrVariable(sqlfInsertObject, containerParameter);
                sqlfInsertObject.append(" AS Container,");
                appendParameterOrVariable(sqlfInsertObject, objecturiParameter);
                sqlfInsertObject.append(" AS ObjectURI, ");
                Integer ownerObjectId = updatable.getOwnerObjectId();
                sqlfInsertObject.append( null == ownerObjectId ? "NULL" : String.valueOf(ownerObjectId) ).append(" AS OwnerObjectId");
                sqlfInsertObject.append(" WHERE NOT EXISTS (SELECT ObjectURI FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfInsertObject, containerParameter);
                sqlfInsertObject.append(" AND ").append(sqlfWhereObjectURI).append(");\n");

                // re-grab the object's ObjectId, in case it was just inserted
                sqlfSelectObject.append(setKeyword).append(objectIdVar).append(" = (");
                sqlfSelectObject.append("SELECT ObjectId FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfSelectObject, containerParameter);
                sqlfSelectObject.append(" AND ").append(sqlfWhereObjectURI).append(");\n");

                if (Operation.insert != _operation && (!properties.isEmpty() || !_vocabularyProperties.isEmpty()))
                {
                    // Clear out any existing property values for this domain
                    if (!properties.isEmpty())
                    {
                        appendSQLFDeleteObjectProperty(sqlfDelete, objectIdVar, properties, null);
                    }

                    // Clear out any existing ad hoc property
                    if (!_vocabularyProperties.isEmpty())
                    {
                        appendSQLFDeleteObjectProperty(sqlfDelete, objectIdVar, null, _vocabularyProperties);
                    }
                }
            }
        }

        if (_selectObjectUri)
        {
            if (objectURIVar == null)
            {
                objectURIVar = _dialect.isPostgreSQL() ? "_$objecturi$_" : "@_objecturi_";
                sqlfDeclare.append("DECLARE ").append(objectURIVar).append(" ").append(_dialect.getSqlTypeName(JdbcType.VARCHAR)).append("(300);\n");
            }

            if (!objectUriPreselectSet && (hasObjectURIColumn || !_vocabularyProperties.isEmpty()))
            {
                setObjectUriPreselect(sqlfPreselectObject, table, keys, objectURIVar, objectURIColumnName, objecturiParameter);
            }
        }


        //
        // BASE TABLE INSERT()
        //

        List<ColumnInfo> cols = new ArrayList<>();
        List<SQLFragment> values = new ArrayList<>();

        if (_updateBuiltInColumns && Operation.update != _operation)
        {
            col = table.getColumn("Owner");
            if (null != col && null != user)
            {
                cols.add(col);
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("Owner");
            }
            col = table.getColumn("CreatedBy");
            if (null != col && null != user)
            {
                cols.add(col);
                values.add(new SQLFragment().append(user.getUserId()));
                done.add("CreatedBy");
            }
            col = table.getColumn("Created");
            if (null != col)
            {
                cols.add(col);
                values.add(new SQLFragment("CURRENT_TIMESTAMP"));   // Instead of {fn now()} -- see #27534
                done.add("Created");
            }
        }

        ColumnInfo colModifiedBy = table.getColumn("ModifiedBy");
        if (_updateBuiltInColumns && null != colModifiedBy && null != user)
        {
            cols.add(colModifiedBy);
            values.add(new SQLFragment().append(user.getUserId()));
            done.add("ModifiedBy");
        }

        ColumnInfo colModified = table.getColumn("Modified");
        if (_updateBuiltInColumns && null != colModified)
        {
            cols.add(colModified);
            values.add(new SQLFragment("CURRENT_TIMESTAMP"));   // Instead of {fn now()} -- see #27534
            done.add("Modified");
        }
        ColumnInfo colVersion = table.getVersionColumn();
        if (_updateBuiltInColumns && null != colVersion && !done.contains(colVersion.getName()))
        {
            SQLFragment expr = colVersion.getVersionUpdateExpression();
            if (null != expr)
            {
                cols.add(colVersion);
                values.add(expr);
                done.add(colVersion.getName());
            }
        }

        String objectIdColumnName = StringUtils.trimToNull(updatable.getObjectIdColumnName());
        ColumnInfo autoIncrementColumn = null;
        CaseInsensitiveHashMap<String> remap = updatable.remapSchemaColumns();
        if (null == remap)
            remap = CaseInsensitiveHashMap.of();

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
            ColumnInfo updatableColumn = updatable.getColumn(column.getName());
            if (updatableColumn != null && updatableColumn.hasDbSequence())
                _dontUpdateColumnNames.add(column.getName());

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
            cols.add(column);
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
                for (ColumnInfo colInfo : cols)
                {
                    sqlfInsertInto.append(comma);
                    comma = ", ";
                    sqlfInsertInto.append(new SQLFragment(colInfo.getSelectName()));
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
                if (useVariables)
                    rowIdVar = "_rowid_";
                rowIdVar = _dialect.addReselect(sqlfInsertInto, autoIncrementColumn, rowIdVar);
                if (useVariables)
                    sqlfDeclare.append("DECLARE ").append(rowIdVar).append(" INT;\n");  // TODO: Move this into addReselect()?
            }

            if(_selectObjectUri && hasObjectURIColumn)
            {
                _dialect.addReselect(sqlfInsertInto, table.getColumn(objectURIColumnName), objectURIVar);
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
                FieldKey fk = cols.get(i).getFieldKey();
                if (keys.containsKey(fk) || null != _dontUpdateColumnNames && _dontUpdateColumnNames.contains(cols.get(i).getName()))
                    continue;
                sqlfUpdate.append(comma);
                comma = ", ";
                sqlfUpdate.append(new SQLFragment(cols.get(i).getSelectName()));
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

        if ((_selectIds && (null != objectIdVar || null != rowIdVar)) || (_selectObjectUri && null != objectURIVar))
        {
            sqlfSelectIds = new SQLFragment("SELECT ");
            comma = "";
            if (_selectIds)
            {
                if (null != rowIdVar)
                {
                    sqlfSelectIds.append(rowIdVar);
                    comma = ",";
                }
                if (null != objectIdVar)
                {
                    sqlfSelectIds.append(comma).append(objectIdVar);
                    comma = ",";
                }
            }

            if (_selectObjectUri &&  null != objectURIVar)
                sqlfSelectIds.append(comma).append(objectURIVar);
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
                appendSQLFObjectProperty(sqlfObjectProperty, dp, objectIdVar, ifTHEN, ifEND);
            }
        }

        if (!_vocabularyProperties.isEmpty())
        {
            for (DomainProperty vocProp: _vocabularyProperties)
            {
                appendSQLFObjectProperty(sqlfObjectProperty, vocProp, objectIdVar, ifTHEN, ifEND);
            }
        }

        //
        // PREPARE
        //

        ParameterMapStatement ret;

        if (!useVariables)
        {
            SQLFragment script = new SQLFragment();
            Stream.of(sqlfDeclare, sqlfPreselectObject, sqlfInsertObject, sqlfSelectObject, sqlfDelete, sqlfUpdate, sqlfInsertInto, sqlfObjectProperty, sqlfSelectIds)
                .filter(f -> null != f && !f.isEmpty())
                .forEach(script::append);
            ret = new ParameterMapStatement(table.getSchema().getScope(), conn, script, remap);
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
            ret = new ParameterMapStatement(table.getSchema().getScope(), conn, script, remap);
        }
        else
        {
            // wrap in a function with a single ROW() constructor argument
            SQLFragment fn = new SQLFragment();
            String fnName = _dialect.getGlobalTempTablePrefix() + "fn_" + GUID.makeHash();
            TempTableTracker.track(fnName, fn);

            String typeName = fnName + "type";
            fn.append("CREATE TYPE ").append(typeName).append(" AS (");
            // TODO d.execute() doesn't handle temp schema
            SQLFragment call = new SQLFragment();
            call.append(fnName).append("(ROW(");
            comma = "";
            for (Map.Entry<String, ParameterHolder> e : parameters.entrySet())
            {
                ParameterHolder ph = e.getValue();
                String type = _dialect.getSqlTypeName(ph.p.getType());
                fn.append("\n").append(comma);
                fn.append(makePgRowTypeName(ph.variableName));
                fn.append(" ");
                fn.append(type);
                // For PG (29687) we need the length for CHAR type
                if (_dialect.isPostgreSQL() && JdbcType.CHAR.equals(ph.p.getType()))
                    fn.append("(").append(ph.getScale()).append(")");
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
                call.append("As x(");
                String sep = "";

                if (_selectIds)
                {
                    if (null != rowIdVar)
                    {
                        call.append("A int");
                        sep = ", ";
                    }
                    if (null != objectIdVar)
                    {
                        call.append(sep);
                        call.append("B int");
                        sep = ", ";
                    }
                }

                if (_selectObjectUri && null != objectURIVar)
                {
                    call.append(sep);
                    call.append("C varchar");
                }

                call.append(");");
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
            ret = new ParameterMapStatement(table.getSchema().getScope(), conn, call, updatable.remapSchemaColumns());
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

        int selectIndex = 1;

        if (_selectIds)
        {
            // Why is one of these boolean and the other an index?? I don't know
            ret.setSelectRowId(selectAutoIncrement);

            if (selectAutoIncrement)
                selectIndex++;

            if (null != objectIdVar)
                ret.setObjectIdIndex(selectIndex++);
        }

        if (_selectObjectUri && null != objectURIVar)
            ret.setObjectUriIndex(selectIndex);

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
        assert(_dialect.isSqlServer());
        String variable = ph.variableName;
        sqlfDeclare.append(variable);
        sqlfDeclare.append(" ");
        JdbcType jdbcType = ph.p.getType();
        assert null != jdbcType;
        String type = _dialect.getSqlTypeName(jdbcType);
        assert null != type;

        // Workaround - SQLServer doesn't support TEXT, NTEXT, or IMAGE as local variables in statements, but is OK with NVARCHAR(MAX)
        if (jdbcType.isText())
        {
            if ("NTEXT".equalsIgnoreCase(type) || "TEXT".equalsIgnoreCase(type) || ph.getScale()>4000)
                type = "NVARCHAR(MAX)";
            else
                type = "NVARCHAR(4000)";
        }
        // Add scale and precision for decimal values specifying scale
        else if (jdbcType.isDecimal() && ph.getScale() > 0)
        {
            type = type + "(" + ph.getPrecision() + "," + ph.getScale() + ")";
        }

        sqlfDeclare.append(type);
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
            ParameterMapStatement m = null;
            try (Connection conn = principals.getSchema().getScope().getConnection())
            {
                m = StatementUtils.insertStatement(conn, principals, null, container, user, null, true, true, false);
//                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;

                m = StatementUtils.insertStatement(conn, test, null, container, user, null, true, true, false);
//                System.err.println(m.getDebugSql()+"\n\n");
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
            ParameterMapStatement m = null;
            try (Connection conn = principals.getSchema().getScope().getConnection())
            {
                m = StatementUtils.updateStatement(conn, principals, container, user, true, true);
//                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;

                m = StatementUtils.updateStatement(conn, test, container, user, true, true);
//                System.err.println(m.getDebugSql()+"\n\n");
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
            ParameterMapStatement m = null;
            try (Connection conn = principals.getSchema().getScope().getConnection())
            {
                m = StatementUtils.mergeStatement(conn, principals, null, null, container, user, false, true);
//                System.err.println(m.getDebugSql()+"\n\n");
                m.close(); m = null;

                m = StatementUtils.mergeStatement(conn, test, null, null, container, user, false, true);
//                System.err.println(m.getDebugSql()+"\n\n");
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
