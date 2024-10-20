/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.query.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: matthewb
 * Date: Feb 17, 2009
 * Time: 2:18:05 PM
 *
 * A query relation represent a portion of a query with column list.  All columns exported by a relation
 * must have a one part name.  
 */
public abstract class AbstractQueryRelation implements QueryRelation
{
    private static final Logger _log = LogManager.getLogger(AbstractQueryRelation.class);

    protected String _savedName = null;
    protected Query _query;
    protected QuerySchema _schema;
    protected String _alias = null;
    protected final String _guid = GUID.makeGUID();
    private CommonTableExpressions _commonTableExpressions = null;

    // used to resolve column in outer scope
    protected QueryRelation _parent;
    protected boolean _inFromClause = true;


    protected AbstractQueryRelation(Query query)
    {
        _query = query;
        _schema = query.getSchema();
        MemTracker.getInstance().put(this);
    }


    protected AbstractQueryRelation(Query query, QuerySchema schema, String alias)
    {
        _query = query;
        _schema = schema;
        _alias = alias;
        MemTracker.getInstance().put(this);
    }

    public void setAlias(String alias)
    {
        _alias = alias;
    }

    @Override
    public void setParent(QueryRelation parent)
    {
        _parent = parent;
    }

    /* for debugging */
    @Override
    public void setSavedName(String name)
    {
        _savedName = name;
    }

    @Override
    public void setQuery(Query query) // reparent the relation
    {
        _query = query;
    }

    @Override
    public QuerySchema getSchema()
    {
        return _schema;
    }

    @Override
    public List<QueryException> getParseErrors()
    {
        return _query.getParseErrors();
    }

    @Override
    public void reportWarning(String string, @Nullable QNode node)
    {
        _query.reportWarning(string, null==node?0:node.getLine(), null==node?0:node.getColumn());
    }


    @Override
    public Collection<String> getKeyColumns()
    {
        return Collections.emptyList();
    }

    @Override
    public SQLFragment getFromSql()
    {
        SQLFragment sql = getSql();
        if (null == sql)
            return null;
        SQLFragment ret = new SQLFragment();
        ret.append("(");
        ret.append(sql);
        ret.append(") ");
        ret.append(getAlias());
        return ret;
    }


    @Override
    public String getAlias()
    {
        return _alias;
    }


    /** declare that this FieldKey is referenced somewhere in this query (or subquery) */
    @Override
    public RelationColumn declareField(FieldKey key, QExpr location)
    {
        if (_parent != null && !_inFromClause)
            return _parent.declareField(key, location);
        return null;
    }


    /** a QField wraps a reference to a QueryRelation and a field name */
    @Override
    public QField getField(FieldKey key, QNode expr, Object referant)
    {
        if (_parent != null && !_inFromClause)
            return _parent.getField(key, expr, referant);
        return new QField(null, key.getName(), expr);
    }

    @Override
    public MethodInfo getMethod(String name)
    {
        return null;
    }


    @Override
    public int getNestingLevel()
    {
        if (_parent == null)
            return 0;
        return _parent.getNestingLevel() + 1;
    }

    //NOTE: column order is important when generating the suggested column list
    //subclasses should implement _getSuggestedColumns() instead of overriding this
    @Override
    public final Set<RelationColumn> getOrderedSuggestedColumns(Set<RelationColumn> selected)
    {
        Set<RelationColumn> suggested = getSuggestedColumns(selected);
        TreeSet<RelationColumn> ret = new TreeSet<>(Comparator.comparing(RelationColumn::getAlias));

        ret.addAll(suggested);

        return ret;
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        RelationColumn col = getColumn("container");
        if (col == null)
            col = getColumn("folder");
        return col != null ? col.getFieldKey() : null;
    }

    /**
     * Return the resolved tables for this query
     */
    @Override
    public Set<SchemaKey> getResolvedTables()
    {
        return _query.getResolvedTables();
    }

    @Override
    public CommonTableExpressions getCommonTableExpressions()
    {
        return _commonTableExpressions;
    }

    public void setCommonTableExpressions(CommonTableExpressions queryWith)
    {
        _commonTableExpressions = queryWith;
    }

    /**
     * Why RelationColumn??
     * yes, it is similar to ColumnInfo and I might have been able to make that work.  However,
     * ColumnInfo's belong to TableInfo (not QueryRelation) and TableInfo's are not mutable,
     * I suppose I could have created a mutable TableInfo subclass, and wrapped schema tableinfos
     * with QueryTableInfo's etc... But I didn't. I have a light-weight class that wraps
     * schema ColumnInfo's and represents internal uses of columns
     */
    public abstract static class RelationColumn
    {
        boolean _suggestedColumn = false;
        String _uniqueName = null;

        public abstract FieldKey getFieldKey();     // field key does NOT include table name/alias
        abstract String getAlias();
        abstract AbstractQueryRelation getTable();
        abstract boolean isHidden();
        abstract String getPrincipalConceptCode();    // used to implement Table.column() method
        abstract String getConceptURI();

        @NotNull
        public abstract JdbcType getJdbcType();

        public SQLFragment getValueSql()
        {
            assert ref.count() > 0;
            String tableName = getTable().getAlias();
            String columnName = getDialect(this).makeLegalIdentifier(getAlias());
            return new SQLFragment(tableName + "." + columnName);
        }

        abstract void copyColumnAttributesTo(@NotNull BaseColumnInfo to);

        ColumnLogging getColumnLogging()
        {
            // columns that implement ColumnResolvingRelation, should override this method
            // that's kinda the point.
            assert !(getTable() instanceof ColumnResolvingRelation);
            return null;
        }


        PHI getPHI()
        {
            assert !(getTable() instanceof ColumnResolvingRelation);
            return null;
        }

        /** See QNode.gatherInvolvedSelectColumns() and interface QueryRelation.ColumnResolvingRelation*/
        abstract public Collection<AbstractQueryRelation.RelationColumn> gatherInvolvedSelectColumns(Collection<AbstractQueryRelation.RelationColumn> collect);

        // the sql representing this column 'inside' its queryrelation (optional)
        SQLFragment getInternalSql()
        {
            throw new UnsupportedOperationException();    
        }

        void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
        }

        public ForeignKey getFk()
        {
            return null;
        }

        protected static SqlDialect getDialect(RelationColumn c)
        {
            return c.getTable()._schema.getDbSchema().getSqlDialect();
        }

        @Override
        public String toString()
        {
            return (null == getFieldKey() ? "" : (getFieldKey().toDisplayString() + " ")) + super.toString();
        }


        protected ReferenceCount ref = new ReferenceCount(null);

        public int addRef(@NotNull Object refer)
        {
            int i = ref.increment(refer);
            _log.debug("addRef( " + this.getDebugString() + ", " + refer + " ) = " + i);
            return i;
        }

        public int releaseRef(@NotNull Object refer)
        {
            return ref.decrement(refer);
        }

        public boolean isReferencedByOthers(Object refer)
        {
            int count = ref.count();
            if (count == 1)
                return !ref.isReferencedBy(refer);
            return count != 0;
        }


        /**
         * Within a query, column names can be slippery.  We give each column a unique internal name.
         * This facilitates correctly reconnecting related columns after a query is parsed.
         *
         * Given "FROM TableA as X, TableB as Y", column X.value and Y.value are considered different columns
         * and will have different unique identifiers.
         *
         * @return query wide unique identifier for this column.
         */
        public String getUniqueName()
        {
            // Don't recompute uniquename on the fly. QueryUnion.getAlias() can change, and we use getAlias() for some readability.
            if (null == _uniqueName)
                _uniqueName = _defaultUniqueName(getTable());
            return _uniqueName;
        }

        public static final String UNIQUE_NAME_PREFIX = ".--UN--";
        public static final String UNIQUE_NAME_SUFFIX = ".-- ";

        protected String _defaultUniqueName(AbstractQueryRelation r)
        {
            // all unique names should start with some recognizable prefix/suffix so that we can assert that these names don't escape from Query somehow
            // r_guid + identityHashCode(column/this) should be unique, the other stuff is for readability
            Objects.requireNonNull(r.getAlias());
            return UNIQUE_NAME_PREFIX + r._guid + "." + System.identityHashCode(this) + "." + this.getClass().getSimpleName() + "." + r.getAlias() + "." + getAlias() + UNIQUE_NAME_SUFFIX;
        }

        public static boolean isUniqueName(String s)
        {
            return s.startsWith(UNIQUE_NAME_PREFIX) && s.endsWith(UNIQUE_NAME_SUFFIX);
        }

        public String getDebugString()
        {
            QueryRelation r = getTable();
            if (null == r)
                return getAlias();
            else
                return r.toStringDebug() + "." + getAlias();
        }
    }


    // like an AliasedColumnInfo
    static public class RelationColumnInfo extends BaseColumnInfo
    {
        RelationColumn _column;

        public RelationColumnInfo(TableInfo parent, RelationColumn column)
        {
            super(column.getFieldKey(), parent);
            setAlias(column.getAlias());
            column.copyColumnAttributesTo(this);
            _column = column;
        }

        @Override
        public SQLFragment getValueSql(String tableAlias)
        {
            return new SQLFragment(tableAlias + "." + getParentTable().getSqlDialect().makeLegalIdentifier(getAlias()));
        }

        @Override
        public boolean isAdditionalQueryColumn()
        {
            return _column._suggestedColumn;
        }
    }


    @Override
    public String toStringDebug()
    {
        if (null == _parent)
            return getClass().getSimpleName() + ":" +getAlias();
        else
            return _parent.toStringDebug() + "/" + getClass().getSimpleName() + ":" +getAlias();
    }


    @Override
    public List<Sort.SortField> getSortFields()
    {
        return List.of();
    }


    @Override
    public boolean equals(Object o)
    {
        if (o instanceof AbstractQueryRelation qr)
            return _guid.equals(qr._guid);
        return false;
    }

    @Override
    public int hashCode()
    {
        return _guid.hashCode();
    }
}
