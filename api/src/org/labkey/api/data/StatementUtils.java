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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.dialect.MockSqlDialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.TableInsertUpdateDataIterator;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.labkey.api.util.JunitUtil.deleteTestContainer;

public class StatementUtils
{
    private static final Logger _log = LogHelper.getLogger(StatementUtils.class, "SQL insert/update/delete generation");

    public enum Operation {insert, update, merge}

    // configuration parameters
    private Operation _operation;
    private SqlDialect _dialect;
    private final TableInfo _targetTable;
    private Set<String> _keyColumnNames = null;       // override the primary key of _table
    private Set<String> _skipColumnNames = Set.of();
    private final Set<String> _dontUpdateColumnNames = new CaseInsensitiveHashSet();
    private boolean _updateBuiltInColumns = false;      // default to false, this should usually be handled by StandardDataIteratorBuilder
    private boolean _selectIds = false;
    private boolean _selectObjectUri = false;
    private boolean _allowUpdateAutoIncrement = false;
    private boolean _preferPKOverObjectUriAsKey = false;

    // variable/parameter tracking helpers
    private boolean useVariables = false;
    private final Map<String, Object> _constants = new CaseInsensitiveHashMap<>();
    final Map<String, ParameterHolder> parameters = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());

    // ColumnTracker is used for test instrumentation and contains sets of column names that were included in
    // given operations (insert, update, select) after running createStatement().
    // This is intended for test instrumentation use only.
    private record ColumnTracker(Set<String> insertColumns, Set<String> updateColumns, Set<String> selectColumns)
    {
        public ColumnTracker()
        {
            this(new CaseInsensitiveHashSet(), new CaseInsensitiveHashSet(), new CaseInsensitiveHashSet());
        }
    }

    private ColumnTracker _columnTracker;

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

    public StatementUtils setPreferPKOverObjectUriAsKey(boolean preferPKOverObjectUriAsKey)
    {
        _preferPKOverObjectUriAsKey = preferPKOverObjectUriAsKey;
        return this;
    }

    private static StatementUtils insertStatement(TableInfo table, boolean selectIds, boolean autoFillDefaultColumns)
    {
        return new StatementUtils(Operation.insert, table)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds);
    }

    /**
     * Create a reusable SQL Statement for inserting rows into a labkey relationship. The relationship
     * persisted directly in the database (SchemaTableInfo), or via the OntologyManager tables.
     * <p>
     * QueryService shouldn't really know about the internals of exp.Object and exp.ObjectProperty etc.
     * However, I can only keep so many levels of abstraction in my head at once.
     * <p>
     * NOTE: this is currently fairly expensive for updating one row into an Ontology stored relationship on Postgres.
     * This shouldn't be a big problem since we don't usually need to optimize the one-row case, and we're moving
     * to provisioned tables for major datatypes.
     */
    public static ParameterMapStatement insertStatement(Connection conn, TableInfo table, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return insertStatement(table, selectIds, autoFillDefaultColumns)
                .createStatement(conn, c, user);
    }

    private static StatementUtils updateStatement(TableInfo table, boolean selectIds, boolean autoFillDefaultColumns)
    {
        return new StatementUtils(Operation.update, table)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds);
    }

    /**
     * Create a reusable SQL Statement for updating rows into a labkey relationship. The relationship
     * persisted directly in the database (SchemaTableInfo), or via the OntologyManager tables.
     * <p>
     * QueryService shouldn't really know about the internals of exp.Object and exp.ObjectProperty etc.
     * However, I can only keep so many levels of abstraction in my head at once.
     * <p>
     * NOTE: this is currently fairly expensive for updating one row into an Ontology stored relationship on Postgres.
     * This shouldn't be a big problem since we don't usually need to optimize the one-row case, and we're moving
     * to provisioned tables for major datatypes.
     */
    public static ParameterMapStatement updateStatement(Connection conn, TableInfo table, @Nullable Container c, User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        return updateStatement(table, selectIds, autoFillDefaultColumns)
                .createStatement(conn, c, user);
    }

    private static StatementUtils mergeStatement(TableInfo table, @Nullable Set<String> keyNames, @Nullable Set<String> skipColumnNames, @Nullable Set<String> dontUpdate, boolean selectIds, boolean autoFillDefaultColumns, boolean supportsAutoIncrementKey)
    {
        return new StatementUtils(Operation.merge, table)
                .keys(keyNames)
                .skip(skipColumnNames)
                .allowSetAutoIncrement(supportsAutoIncrementKey)
                .noupdate(dontUpdate)
                .updateBuiltinColumns(autoFillDefaultColumns)
                .selectIds(selectIds);
    }

    public static ParameterMapStatement mergeStatement(Connection conn, TableInfo table, @Nullable Set<String> keyNames, @Nullable Set<String> skipColumnNames, @Nullable Set<String> dontUpdate, @Nullable Container c, @Nullable User user, boolean selectIds, boolean autoFillDefaultColumns, boolean supportsAutoIncrementKey) throws SQLException
    {
        return mergeStatement(table, keyNames, skipColumnNames, dontUpdate, selectIds, autoFillDefaultColumns, supportsAutoIncrementKey)
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
            var type = requireNonNull(p.getType());
            if (null == _columnInfo || _columnInfo.getScale() <= 0)
                return -1;
            // GUID.isText()==true
            if (JdbcType.GUID != type && (type.isText() || type.isDecimal()))
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

        public String getSqlTypeName(SqlDialect dialect)
        {
            var jdbcType = p.getType();
            if (JdbcType.ARRAY == jdbcType && _columnInfo != null && _columnInfo.getPropertyType() == PropertyType.MULTI_CHOICE)
                return "text[]";
            return dialect.getSqlTypeName(jdbcType);
        }
    }

    private final static String pgRowVarPrefix = "$1.";
    private String makeVariableName(String name)
    {
        String shortName = StringUtils.substring(name,0,32); // name is just for readability, make it short
        String uniquePrefix = (_dialect.isSqlServer() ? "@" : pgRowVarPrefix) + ("p" + (parameters.size()+1) + "_");
        return uniquePrefix + AliasManager.makeLegalName(shortName, _dialect, true, uniquePrefix.length());
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
        sqlfObjectProperty.append(",").appendValue(dp.getPropertyId());
        sqlfObjectProperty.append(",").appendStringLiteral(String.valueOf(propertyType.getStorageType()), _dialect);
        sqlfObjectProperty.append(",");
        if (dp.isMvEnabled())
            appendParameterOrVariable(sqlfObjectProperty, mv);
        else
            sqlfObjectProperty.append("NULL");
        sqlfObjectProperty.append(",");
        appendPropertyValue(sqlfObjectProperty, dp, v);
        sqlfObjectProperty.append(")").appendEOS();
        sqlfObjectProperty.append(ifEND);
        sqlfObjectProperty.appendEOS();
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
            sqlfDelete.appendValue(property.getPropertyId());
        }
        sqlfDelete.append(")").appendEOS();
    }

    private void setObjectUriPreselect(SQLFragment sqlfPreselectObject, TableInfo table, LinkedHashMap<FieldKey, ColumnInfo> keys, String objectURIVar, String objectURIColumnName, ParameterHolder objecturiParameter)
    {
        String setKeyword = _dialect.isPostgreSQL() ? "" : "SET ";
        if (Operation.merge == _operation || Operation.update == _operation)
        {
            // this seems overkill actually, but I'm focused on optimizing insert right now (MAB)
            sqlfPreselectObject.append(setKeyword).append(objectURIVar).append(" = COALESCE((");
            sqlfPreselectObject.append("SELECT ").appendIdentifier(table.getColumn(objectURIColumnName).getSelectIdentifier());
            sqlfPreselectObject.append(" FROM ").append(table.getSQLName());
            sqlfPreselectObject.append(getPkWhereClause(keys));
            sqlfPreselectObject.append("),");
            appendParameterOrVariable(sqlfPreselectObject, objecturiParameter);
            sqlfPreselectObject.append(")").appendEOS();

        }
        else
        {
            sqlfPreselectObject.append(setKeyword).append(objectURIVar).append(" = ");
            appendParameterOrVariable(sqlfPreselectObject, objecturiParameter);
            sqlfPreselectObject.appendEOS();
        }
    }

    public ParameterMapStatement createStatement(Connection conn, @Nullable Container c, User user) throws SQLException
    {
        ParameterMapStatement statement = null;
        try
        {
            statement = createStatement(conn, c, user, false);
        }
        catch (TableInsertUpdateDataIterator.NoUpdatableColumnInDataException e)
        {
            // ignore error
        }
        return statement;
    }

    public ParameterMapStatement createStatement(Connection conn, @Nullable Container c, User user, boolean checkUpdatableColumns) throws SQLException, TableInsertUpdateDataIterator.NoUpdatableColumnInDataException
    {
        if (!(_targetTable instanceof UpdateableTableInfo updatable))
            throw new IllegalArgumentException("Table must be an UpdateableTableInfo");

        TableInfo table = updatable.getSchemaTableInfo();

        if (table.getTableType() != DatabaseTableType.TABLE)
            throw new IllegalArgumentException("Table must be a database table");
        if (null == table.getMetaDataIdentifier())
            throw new IllegalArgumentException("Table must have a metadata identifier");

        if (Operation.merge == _operation)
        {
            if (!_dialect.isPostgreSQL() && !_dialect.isSqlServer())
                throw new IllegalArgumentException("Merge is only supported/tested on postgres and sql server");
        }

        useVariables = Operation.merge == _operation;
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

        //
        // Keys for UPDATE or MERGE
        //
        LinkedHashMap<FieldKey, ColumnInfo> keys = getKeys(updatable, table, objectURIColumnName, _keyColumnNames, _preferPKOverObjectUriAsKey);

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
        DomainKind<?> domainKind = updatable.getDomainKind();
        List<? extends DomainProperty> properties = Collections.emptyList();

        boolean hasObjectURIColumn = objectURIColumnName != null && table.getColumn(objectURIColumnName) != null;
        boolean alwaysInsertExpObject = (hasObjectURIColumn && updatable.isAlwaysInsertExpObject()) && Operation.update != _operation;
        if (hasObjectURIColumn)
            _dontUpdateColumnNames.add(objectURIColumnName);
// TODO Should we add created and createdby? Or make the caller decide?
        if (Operation.update == _operation)
        {
            _dontUpdateColumnNames.add("Created");
            _dontUpdateColumnNames.add("CreatedBy");
        }

        String objectIdVar = null;
        String objectURIVar = null;
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
                sqlfDeclare.append("DECLARE ").append(objectIdVar).append(" BIGINT").appendEOS();
                objectURIVar = _dialect.isPostgreSQL() ? "_$objecturi$_" : "@_objecturi_";
                sqlfDeclare.append("DECLARE ").append(objectURIVar).append(" ").append(_dialect.getSqlTypeName(JdbcType.VARCHAR)).append("(300)").appendEOS();
                useVariables |= _dialect.isPostgreSQL();

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
                Long ownerObjectId = updatable.getOwnerObjectId();
                sqlfInsertObject.append( null == ownerObjectId ? "NULL" : String.valueOf(ownerObjectId) ).append(" AS OwnerObjectId");
                sqlfInsertObject.append(" WHERE NOT EXISTS (SELECT ObjectURI FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfInsertObject, containerParameter);
                sqlfInsertObject.append(" AND ").append(sqlfWhereObjectURI).append(")").appendEOS();

                // re-grab the object's ObjectId, in case it was just inserted
                sqlfSelectObject.append(_dialect.isPostgreSQL() ? "" : "SET ").append(objectIdVar).append(" = (");
                sqlfSelectObject.append("SELECT ObjectId FROM exp.Object WHERE Container = ");
                appendParameterOrVariable(sqlfSelectObject, containerParameter);
                sqlfSelectObject.append(" AND ").append(sqlfWhereObjectURI).append(")").appendEOS();

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
                sqlfDeclare.append("DECLARE ").append(objectURIVar).append(" ").append(_dialect.getSqlTypeName(JdbcType.VARCHAR)).append("(300)").appendEOS();
            }

            if (!objectUriPreselectSet && (hasObjectURIColumn || !_vocabularyProperties.isEmpty()))
            {
                setObjectUriPreselect(sqlfPreselectObject, table, keys, objectURIVar, objectURIColumnName, objecturiParameter);
            }
        }


        //
        // BASE TABLE INSERT()
        //

        ColumnInfo col;
        List<ColumnInfo> cols = new ArrayList<>();
        List<SQLFragment> values = new ArrayList<>();
        Set<String> done = Sets.newCaseInsensitiveHashSet();

        if (_updateBuiltInColumns && Operation.update != _operation)
        {
            col = table.getColumn("Owner");
            if (null != col && null != user)
            {
                cols.add(col);
                values.add(new SQLFragment().appendValue(user.getUserId()));
                done.add("Owner");
            }
            col = table.getColumn("CreatedBy");
            if (null != col && null != user)
            {
                cols.add(col);
                values.add(new SQLFragment().appendValue(user.getUserId()));
                done.add("CreatedBy");
            }
            col = table.getColumn("Created");
            if (null != col)
            {
                cols.add(col);
                values.add(new SQLFragment().appendNowTimestamp());
                done.add("Created");
            }
        }

        ColumnInfo colModifiedBy = table.getColumn("ModifiedBy");
        if (_updateBuiltInColumns && null != colModifiedBy && null != user)
        {
            cols.add(colModifiedBy);
            values.add(new SQLFragment().appendValue(user.getUserId()));
            done.add("ModifiedBy");
        }

        ColumnInfo colModified = table.getColumn("Modified");
        if (_updateBuiltInColumns && null != colModified)
        {
            cols.add(colModified);
            values.add(new SQLFragment().appendNowTimestamp());
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
            if (column instanceof WrappedColumn || column.isCalculated())
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
                if (null != _skipColumnNames && _skipColumnNames.contains(Objects.toString(remap.get(name),name)))
                    continue;
                ParameterHolder ph = createParameter(column);
                appendParameterOrVariable(valueSQL, ph);
            }
            cols.add(column);
            values.add(valueSQL);
        }

        boolean selectAutoIncrement = false;

        assert cols.size() == values.size() : cols.size() + " columns and " + values.size() + " values - should match";

        //
        // INSERT
        //

        String comma;
        String rowIdVar = null;
        SQLFragment sqlfInsertInto = new SQLFragment();

        // Construct a new column tracker for test instrumentation
        _columnTracker = new ColumnTracker();

        if (Operation.insert == _operation || Operation.merge == _operation)
        {
            // Create a standard INSERT INTO table (col1, col2) VALUES (val1, val2) statement
            // or (for degenerate, empty values case) INSERT INTO table VALUES (DEFAULT)
            sqlfInsertInto.append("INSERT INTO ").append(table.getSQLName());

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
                    sqlfInsertInto.appendIdentifier(colInfo.getSelectIdentifier());
                    _columnTracker.insertColumns.add(colInfo.getName());
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
                    sqlfDeclare.append("DECLARE ").append(rowIdVar).append(" BIGINT").appendEOS();  // CONSIDER: Move this into addReselect()?
            }

            if (_selectObjectUri && hasObjectURIColumn)
            {
                _dialect.addReselect(sqlfInsertInto, table.getColumn(objectURIColumnName), objectURIVar);
            }
        }

        //
        // UPDATE
        //

        SQLFragment sqlfUpdate = new SQLFragment();
        if (Operation.update == _operation || Operation.merge == _operation)
        {
            // Create a standard UPDATE table SET col1 = val1, col2 = val2 statement
            sqlfUpdate.append("UPDATE ").append(table.getSQLName()).append("\nSET ");
            comma = "";
            int updateCount = 0;
            for (int i = 0; i < cols.size(); i++)
            {
                col = cols.get(i);
                FieldKey fk = col.getFieldKey();
                if (keys.containsKey(fk))
                    continue;

                // Issue 52666: Check column remapping when looking for columns to not update
                String colName = col.getName();
                if (_dontUpdateColumnNames.contains(colName) || (remap.containsKey(colName) && _dontUpdateColumnNames.contains(remap.get(colName))))
                    continue;

                sqlfUpdate.append(comma);
                comma = ", ";
                sqlfUpdate.appendIdentifier(col.getSelectIdentifier());
                sqlfUpdate.append(" = ");
                sqlfUpdate.append(values.get(i));
                _columnTracker.updateColumns.add(col.getName());
                updateCount++;
            }

            if (Operation.update == _operation && updateCount == 0)
            {
                if (checkUpdatableColumns)
                    throw new TableInsertUpdateDataIterator.NoUpdatableColumnInDataException(table.getName());

                sqlfUpdate.appendIdentifier(keys.values().iterator().next().getSelectIdentifier());
                sqlfUpdate.append(" = 'noop' WHERE 1 <> 1").appendEOS();
            }
            else
            {
                sqlfUpdate.append(getPkWhereClause(keys));
                sqlfUpdate.appendEOS();
            }

            if (Operation.merge == _operation)
            {
                // updateCount can equal 0. This happens particularly when inserting into junction tables where
                // there are two columns and both are in the primary key
                if (0 == updateCount)
                {
                    sqlfUpdate = new SQLFragment();
                    sqlfInsertInto.append("\nWHERE NOT EXISTS (SELECT * FROM ").append(table.getSQLName());
                    sqlfInsertInto.append(getPkWhereClause(keys));
                    sqlfInsertInto.append(")");
                }
                else
                {
                    sqlfUpdate.append("IF ");
                    sqlfUpdate.append(_dialect.isSqlServer() ? "@@ROWCOUNT=0" : "NOT FOUND");
                    sqlfUpdate.append(ifTHEN).append("\n\t");

                    sqlfInsertInto.appendEOS();
                    sqlfInsertInto.append(ifEND);
                }
            }
        }

        if (Operation.insert == _operation || Operation.merge == _operation)
            sqlfInsertInto.appendEOS();

        SQLFragment sqlfSelectIds = null;

        if ((_selectIds && (null != objectIdVar || null != rowIdVar)) || (_selectObjectUri && null != objectURIVar))
        {
            sqlfSelectIds = new SQLFragment("SELECT ");
            comma = "";
            if (_selectIds)
            {
                if (null != rowIdVar)
                {
                    sqlfSelectIds.append(rowIdVar);
                    _columnTracker.selectColumns.add(rowIdVar);
                    comma = ",";
                }
                if (null != objectIdVar)
                {
                    sqlfSelectIds.append(comma).append(objectIdVar);
                    _columnTracker.selectColumns.add(objectIdVar);
                    comma = ",";
                }
            }

            if (_selectObjectUri && null != objectURIVar)
            {
                sqlfSelectIds.append(comma).append(objectURIVar);
                _columnTracker.selectColumns.add(objectIdVar);
            }
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
                    String variable = sqlServerVariableDeclaration(sqlfDeclare, ph);
                    select.append(comma).append(variable).append("=?");
                    select.add(ph.p);
                    comma = ", ";
                }
                sqlfDeclare.appendEOS();
                sqlfDeclare.append(select);
                sqlfDeclare.appendEOS();
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
                String type = ph.getSqlTypeName(_dialect);
                fn.append("\n").append(comma);
                fn.append(makePgRowTypeName(ph.variableName));
                fn.append(" ");
                fn.append(type);
                // For PG (29687) we need the length for CHAR type
                if (_dialect.isPostgreSQL() && JdbcType.CHAR.equals(ph.p.getType()))
                    fn.append("(").appendValue(ph.getScale()).append(")");
                call.append(comma).append("?");
                call.add(ph.p);
                comma = ",";
            }
            fn.append("\n)").appendEOS();
            fn.append("CREATE FUNCTION ").append(fnName).append("(").append(typeName).append(") ");
            fn.append("RETURNS ");
            if (null != sqlfSelectIds)
                fn.append("SETOF RECORD");
            else
                fn.append("void");
            String quoteToken = "$x" + GUID.makeHash() + "$";
            fn.append(" AS ").append(quoteToken).append("\n");
            call.append("))");

            if (null != sqlfSelectIds)
            {
                call.insert(0, "SELECT * FROM ");
                call.append(" AS x(");
                String sep = "";

                if (_selectIds)
                {
                    if (null != rowIdVar)
                    {
                        call.append("A BIGINT");
                        sep = ", ";
                    }
                    if (null != objectIdVar)
                    {
                        call.append(sep);
                        call.append("B BIGINT");
                        sep = ", ";
                    }
                }

                if (_selectObjectUri && null != objectURIVar)
                {
                    call.append(sep);
                    call.append("C VARCHAR");
                }

                call.append(")").appendEOS();
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
                fn.append("RETURN").appendEOS();
            }
            else
            {
                sqlfSelectIds.insert(0, "RETURN QUERY\n");
                fn.append(sqlfSelectIds);
                fn.appendEOS();
            }
            fn.append("END").appendEOS().append(" ").append(quoteToken).append(" LANGUAGE plpgsql").appendEOS();
            _log.debug(fn.toDebugString());
            _log.debug(call.toDebugString());
            final SQLFragment drop = new SQLFragment("DROP TYPE IF EXISTS ").append(typeName).append(" CASCADE").appendEOS();
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

    private static LinkedHashMap<FieldKey, ColumnInfo> getKeys(
        UpdateableTableInfo updatable,
        TableInfo table,
        String objectURIColumnName,
        Set<String> keyColumnNames,
        boolean preferPKOverObjectUriAsKey
    )
    {
        LinkedHashMap<FieldKey, ColumnInfo> keys = new LinkedHashMap<>();
        ColumnInfo col = table.getColumn("Container");

        if (null != col)
            keys.put(col.getFieldKey(), col);

        if (null != keyColumnNames && !keyColumnNames.isEmpty())
        {
            for (String name : keyColumnNames)
            {
                col = table.getColumn(name);
                if (null == col)
                    throw new IllegalArgumentException("Column not found: " + name);
                keys.put(col.getFieldKey(), col);
            }
        }
        else
        {
            // using objectURIColumnName preferentially to be backward compatible with OntologyManager.saveTabDelimited
            //    which in turn is only called by LuminexDataHandler.saveDataRows()
            col = objectURIColumnName == null ? null : table.getColumn(objectURIColumnName);
            if (null != col && !preferPKOverObjectUriAsKey)
                keys.put(col.getFieldKey(), col);
            else
            {
                // See Issue 26661 and Issue 41053
                // NOTE: IMO we should not be using updatable.getPkColumnNames() here! If the caller doesn't want to use the
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

        return keys;
    }

    private SQLFragment getPkWhereClause(LinkedHashMap<FieldKey, ColumnInfo> keys)
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
            sqlfWherePK.appendIdentifier(keyCol.getSelectIdentifier());
            sqlfWherePK.append(" = ");
            appendParameterOrVariable(sqlfWherePK, keyColPh);
            if (keyCol.isNullable())
            {
                sqlfWherePK.append(" OR ");
                sqlfWherePK.appendIdentifier(keyCol.getSelectIdentifier());
                sqlfWherePK.append(" IS NULL AND ");
                appendParameterOrVariable(sqlfWherePK, keyColPh);
                sqlfWherePK.append(" IS NULL");
            }
            sqlfWherePK.append(")");
            and = " AND ";
        }
        return sqlfWherePK;
    }

    private String sqlServerVariableDeclaration(SQLFragment sqlfDeclare, ParameterHolder ph)
    {
        assert(_dialect.isSqlServer());
        String variable = ph.variableName;
        sqlfDeclare.append(variable);
        sqlfDeclare.append(" ");
        JdbcType jdbcType = ph.p.getType();
        assert null != jdbcType;
        String type = ph.getSqlTypeName(_dialect);
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

    /*
     * We could use SQLFragment.appendValue() for most of these. However, here it is important to force
     * the use of inline literal values. SQLFragment.appendValue() does not guarantee that.
     */
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
        if (value instanceof NowTimestamp now)
        {
            f.appendValue(now);
            return;
        }
        if (value instanceof java.sql.Date sqlDate)
        {
            f.append("{d ").append(_dialect.getStringHandler().quoteStringLiteral(DateUtil.formatIsoDate(sqlDate))).append("}");
            return;
        }
        else if (value instanceof java.util.Date date)
        {
            f.append("{ts ").append(_dialect.getStringHandler().quoteStringLiteral(DateUtil.formatIsoDateShortTime(date))).append("}");
            return;
        }
        assert value instanceof String;
        f.append(_dialect.getStringHandler().quoteStringLiteral(String.valueOf(value)));
    }

    @SuppressWarnings("JUnitMalformedDeclaration")
    public static class TestCase extends Assert
    {
        final static String DATA_CLASS_NAME = "StatementUtilsTestDataClass";
        final static String VOCAB_DOMAIN_KIND = "Vocabulary"; // VocabularyDomainKind.KIND_NAME
        final static String VOCAB_DOMAIN_NAME = "StatementUtilsVocabularyDomain";

        final Container container;
        final TableInfo dataClassTable;
        final TableInfo principalsTable;
        final UpdateableTableInfo testTable;
        final User user;
        final Set<String> vocabParameters = CaseInsensitiveHashSet.of("Age", "AgeMVIndicator", "Color", "ColorMVIndicator");
        final Set<DomainProperty> vocabProps;

        // Flag to run tests against one or both (Postgres, SQL Server) SqlDialects. Set to false by default
        // since tests are run in both environments in CI. See "otherSqlDialect".
        final boolean runOtherDialect = false;

        // This is a mock SqlDialect that mocks the alternative SqlDialect configuration to the current configuration.
        // So, if the tests are running in a Postgres environment, then this represents a SQL Server SqlDialect
        // and vice versa. This is useful for getting code coverage across code paths for both dialects in a single
        // test run. Enabled via the "runOtherDialect" flag.
        final SqlDialect otherSqlDialect;

        public TestCase() throws Exception
        {
            container = JunitUtil.getTestContainer();
            user = TestContext.get().getUser();

            dataClassTable = QueryService.get().getUserSchema(user, container, ExpSchema.SCHEMA_EXP_DATA).getTableOrThrow(DATA_CLASS_NAME);
            principalsTable = DbSchema.get("core", DbSchemaType.Module).getTable("principals");
            testTable = DbSchema.get("test", DbSchemaType.Module).getTable("testtable2");

            // Initialize vocab domain properties
            {
                var vocabDomainKind = PropertyService.get().getDomainKindByName(VOCAB_DOMAIN_KIND);
                var vocabDomainURI = vocabDomainKind.generateDomainURI(null, VOCAB_DOMAIN_NAME, container, user);
                var vocabDomain = PropertyService.get().getDomain(container, vocabDomainURI);
                assertNotNull(vocabDomain);
                vocabProps = Set.of(vocabDomain.getPropertyByName("Age"), vocabDomain.getPropertyByName("Color"));
            }

            if (runOtherDialect)
            {
                SqlDialect defaultDialect = principalsTable.getSqlDialect();
                boolean isPostgres = defaultDialect.isPostgreSQL();

                otherSqlDialect = new MockSqlDialect()
                {
                    @Override
                    public String addReselect(SQLFragment sql, ColumnInfo column, @Nullable String proposedVariable)
                    {
                        return defaultDialect.addReselect(sql, column, proposedVariable);
                    }

                    @Override
                    public String getGuidType()
                    {
                        return defaultDialect.getGuidType();
                    }

                    @Override
                    public @Nullable String getSqlTypeName(JdbcType type)
                    {
                        return defaultDialect.getSqlTypeName(type);
                    }

                    @Override
                    public boolean isPostgreSQL()
                    {
                        // Returns true in SQL Server configured environments
                        return !isPostgres;
                    }

                    @Override
                    public boolean isSqlServer()
                    {
                        // Returns true in Postgres configured environments
                        return isPostgres;
                    }
                };
            }
            else
            {
                otherSqlDialect = null;
            }
        }

        @BeforeClass
        public static void createDomains() throws Exception
        {
            var container = JunitUtil.getTestContainer();
            var user = TestContext.get().getUser();

            // Create a data class domain
            ExperimentService.get().createDataClass(container, user, DATA_CLASS_NAME, null, List.of(new GWTPropertyDescriptor("aa", "int")), List.of(), null, null);

            // Create a vocabulary domain
            {
                GWTPropertyDescriptor prop1 = new GWTPropertyDescriptor();
                prop1.setRangeURI("int");
                prop1.setName("Age");
                prop1.setMvEnabled(true);

                GWTPropertyDescriptor prop2 = new GWTPropertyDescriptor();
                prop2.setRangeURI("string");
                prop2.setName("Color");

                GWTDomain<GWTPropertyDescriptor> domain = new GWTDomain<>();
                domain.setName(VOCAB_DOMAIN_NAME);
                domain.setFields(List.of(prop1, prop2));

                DomainUtil.createDomain(VOCAB_DOMAIN_KIND, domain, null, container, user, VOCAB_DOMAIN_NAME, null, false);
            }
        }

        @AfterClass
        public static void cleanup()
        {
            deleteTestContainer();
        }

        @Test
        public void testToLiteral()
        {
            boolean isPostgres = principalsTable.getSqlDialect().isPostgreSQL();

            var statement = new StatementUtils(Operation.insert, principalsTable);
            Function<Object, SQLFragment> runToLiteral = (value) -> {
                var sql = new SQLFragment();
                statement.toLiteral(sql, value);
                return sql;
            };

            var dateLong = 1749759500016L; // Thu Jun 12 2025 13:18:20 GMT-0700 (Pacific Daylight Time)

            // null value
            var actual = runToLiteral.apply(null);
            assertEquals(new SQLFragment("NULL"), actual);

            // Number
            actual = runToLiteral.apply(1234567890);
            assertEquals(new SQLFragment("1234567890"), actual);

            // NowTimestamp
            var now = new NowTimestamp(dateLong);
            actual = runToLiteral.apply(now);
            assertEquals(new SQLFragment().appendValue(now), actual);

            // sql.Date
            var sqlDate = new java.sql.Date(dateLong);
            var dateFormat = new SimpleDateFormat(DateUtil.getStandardDateFormatString());
            var expected = String.format(isPostgres ? "{d '%s'}" : "{d N'%s'}", dateFormat.format(sqlDate));

            actual = runToLiteral.apply(sqlDate);
            assertEquals(new SQLFragment(expected), actual);

            // util.Date
            var utilDate = new java.util.Date(dateLong);
            dateFormat = new SimpleDateFormat(DateUtil.getStandardDateTimeFormatString());
            expected = String.format(isPostgres ? "{ts '%s'}" : "{ts N'%s'}", dateFormat.format(utilDate));

            actual = runToLiteral.apply(utilDate);
            assertEquals(new SQLFragment(expected), actual);
        }

        @Test
        public void testCreateStatementValidation() throws Exception
        {
            try (var conn = getConnection())
            {
                var nonUpdateTable = new VirtualTable<>(DbSchema.get("test", DbSchemaType.Module), "virtualInsanity", null);

                var exception = Assert.assertThrows(IllegalArgumentException.class, () -> new StatementUtils(Operation.merge, nonUpdateTable).createStatement(conn, container, user));
                assertEquals("Table must be an UpdateableTableInfo", exception.getMessage());

                // Unreachable with current mocks
//                var nonDatabaseTable = QueryService.get().getUserSchema(user, container, "core").getTableOrThrow("Principals");
//                exception = Assert.assertThrows(IllegalArgumentException.class, () -> new StatementUtils(Operation.merge, nonDatabaseTable).createStatement(conn, container, user));
//                assertEquals("Table must be a database table", exception.getMessage());

//                exception = Assert.assertThrows(IllegalArgumentException.class, () -> {
//                    var noIdentifierTable = principalsTable.getMetaDataIdentifier().
//                    new StatementUtils(Operation.merge, nonDatabaseTable).dialect(new MockSqlDialect()).createStatement(conn, container, user);
//                });
//                assertEquals("Table must have a metadata identifier", exception.getMessage());

                exception = Assert.assertThrows(IllegalArgumentException.class, () -> new StatementUtils(Operation.merge, principalsTable).dialect(new MockSqlDialect()).createStatement(conn, container, user));
                assertEquals("Merge is only supported/tested on postgres and sql server", exception.getMessage());
            }
        }

        @Test
        public void testGetKeys()
        {
            var containerFieldKey = FieldKey.fromParts("Container");
            var rowIdFieldKey = FieldKey.fromParts("RowId");
            var textFieldKey = FieldKey.fromParts("Text");

            var updateTable = testTable;
            var table = updateTable.getSchemaTableInfo();

            // Pre-conditions
            var pkColumnNames = new CaseInsensitiveHashSet(testTable.getPkColumnNames());
            assertEquals(2, pkColumnNames.size());
            assertTrue(pkColumnNames.contains(containerFieldKey.getName()));
            assertTrue(pkColumnNames.contains(textFieldKey.getName()));
            assertNotNull(testTable.getColumn(rowIdFieldKey));

            // The "Container" column is always resolved if present on the table
            var keys = StatementUtils.getKeys(updateTable, table, null, Set.of(containerFieldKey.getName()), false);
            assertEquals(1, keys.size());
            assertTrue(keys.containsKey(containerFieldKey));

            // The "Container" column is only resolved even when in the explicit name map
            keys = StatementUtils.getKeys(updateTable, table, null, Set.of(containerFieldKey.getName()), false);
            assertEquals(1, keys.size());
            assertTrue(keys.containsKey(containerFieldKey));

            // The "Container" column is also resolved even when not in the explicit key column map. Other key columns are included as well.
            keys = StatementUtils.getKeys(updateTable, table, null, Set.of(textFieldKey.getName()), false);
            assertEquals(2, keys.size());
            assertTrue(keys.containsKey(containerFieldKey));
            assertTrue(keys.containsKey(textFieldKey));

            // All explicitly named columns should resolve as columns on the table
            var exception = Assert.assertThrows(IllegalArgumentException.class, () -> StatementUtils.getKeys(updateTable, table, null, Set.of(textFieldKey.getName(), "Beep"), false));
            assertEquals("Column not found: Beep", exception.getMessage());

            // Furnish an explicit "objectURIColumnName" and expect it to be included when preferPKOverObjectUriAsKey = false
            keys = StatementUtils.getKeys(updateTable, table, "RowId", null, false);
            assertEquals(2, keys.size());
            assertTrue(keys.containsKey(containerFieldKey));
            assertTrue(keys.containsKey(rowIdFieldKey));

            // Furnish an explicit "objectURIColumnName" and expect it to NOT be included when preferPKOverObjectUriAsKey = true
            keys = StatementUtils.getKeys(updateTable, table, "RowId", null, true);
            assertEquals(2, keys.size());
            assertTrue(keys.containsKey(containerFieldKey));
            assertTrue(keys.containsKey(textFieldKey));

            keys = StatementUtils.getKeys(updateTable, table, null, null, false);
            assertEquals(2, keys.size());
            assertTrue(keys.containsKey(containerFieldKey));
            assertTrue(keys.containsKey(textFieldKey));
        }

        @Test
        public void testInsert() throws Exception
        {
            ParameterMapStatement m = null;
            try (Connection conn = getConnection())
            {
                m = insertStatement(conn, principalsTable, container, user, true, true);
                m.close(); m = null;

                m = insertStatement(conn, testTable, container, user, true, true);
                m.close(); m = null;
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }

        @Test
        public void testInsertWithExtensibleDomain() throws Exception
        {
            ParameterMapStatement m = null;
            try (Connection conn = getConnection(dataClassTable))
            {
                StatementUtils statement;

                // Insert
                {
                    var validateInsert = new Function<StatementUtils, Object>()
                    {
                        @Override
                        public Object apply(StatementUtils s)
                        {
                            boolean isPostgres = s._dialect.isPostgreSQL();

                            assertTrue(s._columnTracker.insertColumns.contains("Created"));
                            assertTrue(s._columnTracker.insertColumns.contains("CreatedBy"));
                            assertTrue(s._columnTracker.insertColumns.contains("Modified"));
                            assertTrue(s._columnTracker.insertColumns.contains("ModifiedBy"));
                            assertTrue(s._columnTracker.updateColumns.isEmpty());

                            if (isPostgres)
                            {
                                assertTrue(s._columnTracker.selectColumns.contains("_rowid_"));
                                assertTrue(s._columnTracker.selectColumns.contains("_$objectid$_"));
                            }
                            else
                            {
                                // Variables are not used in SQL Server
                                assertFalse(s._columnTracker.selectColumns.contains("_rowid_"));
                                assertTrue(s._columnTracker.selectColumns.contains("@_objectid_"));
                            }

                            var parameterKeys = s.parameters.keySet();
                            assertTrue(vocabParameters.stream().noneMatch(parameterKeys::contains));
                            return null;
                        }
                    };

                    statement = insertStatement(dataClassTable, true, true);
                    m = statement.createStatement(conn, container, user);
                    m.close();
                    m = null;
                    validateInsert.apply(statement);

                    if (runOtherDialect)
                    {
                        statement = insertStatement(dataClassTable, true, true);
                        statement.dialect(otherSqlDialect);
                        m = statement.createStatement(conn, container, user);
                        m.close();
                        m = null;
                        validateInsert.apply(statement);
                    }
                }

                // Insert with vocabulary properties
                {
                    statement = insertStatement(dataClassTable, true, true);
                    statement.setVocabularyProperties(vocabProps);
                    m = statement.createStatement(conn, container, user);
                    m.close();
                    m = null;
                    assertTrue(statement.parameters.keySet().containsAll(vocabParameters));
                }
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
            ParameterMapStatement m = null;
            try (Connection conn = getConnection())
            {
                m = updateStatement(conn, principalsTable, container, user, true, true);
                m.close(); m = null;

                m = updateStatement(conn, testTable, container, user, true, true);
                m.close(); m = null;
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }

        @Test
        public void testUpdateWithExtensibleDomain() throws Exception
        {
            ParameterMapStatement m = null;
            try (Connection conn = getConnection(dataClassTable))
            {
                StatementUtils statement;

                // Update
                {
                    var validateUpdate = new Function<StatementUtils, Object>()
                    {
                        @Override
                        public Object apply(StatementUtils s)
                        {
                            assertTrue(s._columnTracker.insertColumns.isEmpty());
                            assertFalse(s._columnTracker.updateColumns.contains("Created"));
                            assertFalse(s._columnTracker.updateColumns.contains("CreatedBy"));
                            assertTrue(s._columnTracker.updateColumns.contains("Modified"));
                            assertTrue(s._columnTracker.updateColumns.contains("ModifiedBy"));
                            assertTrue(s._columnTracker.selectColumns.isEmpty());
                            var parameterKeys = s.parameters.keySet();
                            assertTrue(vocabParameters.stream().noneMatch(parameterKeys::contains));
                            return null;
                        }
                    };

                    statement = updateStatement(dataClassTable, true, true);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;
                    validateUpdate.apply(statement);

                    Set<String> allUpdateColumns = new CaseInsensitiveHashSet(statement._columnTracker.updateColumns);

                    if (runOtherDialect)
                    {
                        statement = updateStatement(dataClassTable, true, true);
                        statement.dialect(otherSqlDialect);
                        m = statement.createStatement(conn, container, user);
                        m.close(); m = null;
                        validateUpdate.apply(statement);
                    }

                    statement = updateStatement(dataClassTable, false, false);
                    statement.noupdate(CaseInsensitiveHashSet.of("Modified", "ModifiedBy"));
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;
                    assertFalse(statement._columnTracker.updateColumns.contains("Modified"));
                    assertFalse(statement._columnTracker.updateColumns.contains("ModifiedBy"));

                    statement = updateStatement(dataClassTable, false, false);
                    statement.noupdate(allUpdateColumns);
                    m = statement.createStatement(conn, container, user);
                    var debugSql = m.getDebugSql();
                    m.close(); m = null;
                    assertTrue(debugSql.contains("'noop' WHERE 1 <> 1"));
                }

                // Update with vocabulary properties
                {
                    statement = updateStatement(dataClassTable, true, true);
                    statement.setVocabularyProperties(vocabProps);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;
                    assertTrue(statement.parameters.keySet().containsAll(vocabParameters));
                }
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }

        @Test
        public void testUpdateWithObjectUriColumn() throws Exception
        {
            // Arrange
            // Create a list
            String listName = "StatementUtilsTestList";
            {
                // Create a list domain
                var listDef = ListService.get().createList(container, listName, ListDefinition.KeyType.AutoIncrementInteger);
                listDef.setKeyName("pk");

                Domain domain = requireNonNull(listDef.getDomain());
                addProperty(domain, "pk", PropertyType.INTEGER);
                addProperty(domain, "name", PropertyType.STRING);

                listDef.save(user);
            }

            ParameterMapStatement m = null;
            TableInfo listTable = requireNonNull(ListService.get().getList(container, listName)).getTable(user, container);
            assertNotNull(listTable);

            try (Connection conn = getConnection(listTable))
            {
                StatementUtils statement;
                var expectedNoUpdateColumns = CaseInsensitiveHashSet.of("EntityId", "Created", "CreatedBy");
                var expectedUpdateColumns = CaseInsensitiveHashSet.of("DIImportHash", "LastIndexed", "Modified", "ModifiedBy", "Name");

                // Update statement (selectIds = true, autoFillDefaultColumns = true)
                {
                    statement = StatementUtils.updateStatement(listTable, true, true);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;

                    // Assert
                    assertEquals(expectedNoUpdateColumns, statement._dontUpdateColumnNames);
                    assertTrue(statement._columnTracker.insertColumns.isEmpty());
                    assertTrue(statement._columnTracker.selectColumns.isEmpty());

                    if (runOtherDialect)
                    {
                        statement = StatementUtils.updateStatement(listTable, true, true);
                        statement.dialect(otherSqlDialect);
                        m = statement.createStatement(conn, container, user);
                        m.close(); m = null;
                    }

                    assertEquals(expectedNoUpdateColumns, statement._dontUpdateColumnNames);
                    assertTrue(statement._columnTracker.insertColumns.isEmpty());
                    assertTrue(statement._columnTracker.selectColumns.isEmpty());

                    assertEquals(expectedUpdateColumns, statement._columnTracker.updateColumns);
                }

                // Update statement (selectIds = false, autoFillDefaultColumns = false)
                {
                    statement = StatementUtils.updateStatement(listTable, false, false);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;

                    // Assert
                    assertEquals(expectedNoUpdateColumns, statement._dontUpdateColumnNames);
                    assertTrue(statement._columnTracker.insertColumns.isEmpty());
                    assertEquals(expectedUpdateColumns, statement._columnTracker.updateColumns);

                    if (runOtherDialect)
                    {
                        statement = StatementUtils.updateStatement(listTable, false, false);
                        statement.dialect(otherSqlDialect);
                        m = statement.createStatement(conn, container, user);
                        m.close(); m = null;
                    }

                    assertEquals(expectedNoUpdateColumns, statement._dontUpdateColumnNames);
                    assertTrue(statement._columnTracker.insertColumns.isEmpty());
                    assertEquals(expectedUpdateColumns, statement._columnTracker.updateColumns);
                }
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
            ParameterMapStatement m = null;
            try (Connection conn = getConnection())
            {
                m = mergeStatement(conn, principalsTable, null, null, null, container, user, false, true, false);
                m.close(); m = null;

                if (runOtherDialect)
                {
                    StatementUtils statement = mergeStatement(principalsTable, null, null, null, false, true, false);
                    statement.dialect(otherSqlDialect);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;
                }

                m = mergeStatement(conn, testTable, null, null, null, container, user, false, true, false);
                m.close(); m = null;

                if (runOtherDialect)
                {
                    StatementUtils statement = mergeStatement(testTable, null, null, null, false, true, false);
                    statement.dialect(otherSqlDialect);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;
                }
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }

        @Test
        public void testMergeWithExtensibleDomain() throws Exception
        {
            ParameterMapStatement m = null;
            try (Connection conn = getConnection(dataClassTable))
            {
                StatementUtils statement;

                // Merge
                {
                    var validateMerge = new Function<StatementUtils, Object>()
                    {
                        @Override
                        public Object apply(StatementUtils s)
                        {
                            boolean isPostgres = s._dialect.isPostgreSQL();

                            assertTrue(s._columnTracker.insertColumns.contains("Container"));
                            assertTrue(s._columnTracker.insertColumns.contains("LSID"));
                            assertFalse(s._columnTracker.updateColumns.contains("Container"));
                            assertFalse(s._columnTracker.updateColumns.contains("LSID"));

                            if (isPostgres)
                            {
                                assertTrue(s._columnTracker.selectColumns.contains("_rowid_"));
                                assertTrue(s._columnTracker.selectColumns.contains("_$objectid$_"));
                            }
                            else
                            {
                                // Variables are not used in SQL Server
                                assertFalse(s._columnTracker.selectColumns.contains("_rowid_"));
                                assertTrue(s._columnTracker.selectColumns.contains("@_objectid_"));
                            }

                            var parameterKeys = s.parameters.keySet();
                            assertTrue(vocabParameters.stream().noneMatch(parameterKeys::contains));
                            return null;
                        }
                    };

                    statement = mergeStatement(dataClassTable, null, null, null, true, true, false);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;
                    validateMerge.apply(statement);

                    var updateColumns = new CaseInsensitiveHashSet(statement._columnTracker.updateColumns);

                    if (runOtherDialect)
                    {
                        statement = mergeStatement(dataClassTable, null, null, null, true, true, false);
                        statement.dialect(otherSqlDialect);
                        m = statement.createStatement(conn, container, user);
                        m.close(); m = null;
                        validateMerge.apply(statement);
                    }

                    // TODO: This generates a SQL parsing error in Postgres due to the reselect statement coming before the WHERE clause
//                    statement = mergeStatement(dataClassTable, null, CaseInsensitiveHashSet.of("RunId"), updateColumns, true, true, false);
//                    m = statement.createStatement(conn, container, user);
//                    m.close(); m = null;
//                    assertTrue(statement._columnTracker.updateColumns.isEmpty());
                }

                // Merge with vocabulary properties
                {
                    statement = mergeStatement(dataClassTable, null, null, null, false, true, false);
                    statement.setVocabularyProperties(vocabProps);
                    m = statement.createStatement(conn, container, user);
                    m.close(); m = null;
                    assertTrue(statement.parameters.keySet().containsAll(vocabParameters));
                }
            }
            finally
            {
                if (null != m)
                    m.close();
            }
        }

        private static void addProperty(Domain d, String name, PropertyType pt)
        {
            DomainProperty p = d.addProperty();
            p.setName(name);
            p.setPropertyURI(d.getTypeURI() + "#" + name);
            p.setRangeURI(pt.getTypeUri());
        }

        private Connection getConnection() throws SQLException
        {
            return getConnection(principalsTable);
        }

        private Connection getConnection(TableInfo table) throws SQLException
        {
            return table.getSchema().getScope().getConnection();
        }
    }
}
