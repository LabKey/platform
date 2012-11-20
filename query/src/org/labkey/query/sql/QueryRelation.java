/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.MemTracker;
import org.labkey.data.xml.ColumnType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: Feb 17, 2009
 * Time: 2:18:05 PM
 *
 * A query relation represent a portion of a query with column list.  All columns exported by a relation
 * must have a one part name.  
 */
public abstract class QueryRelation
{
    protected String _savedName = null;
    protected Query _query;
    protected QuerySchema _schema;
    protected String _alias = null;

    // used to resolve column in outer scope
    protected QueryRelation _parent;
    protected boolean _inFromClause = true;


    protected QueryRelation(Query query)
    {
        _query = query;
        _schema = query.getSchema();
        assert MemTracker.put(this);
    }


    protected QueryRelation(Query query, QuerySchema schema, String alias)
    {
        _query = query;
        _schema = schema;
        _alias = alias;
        assert MemTracker.put(this);
    }

    protected void setAlias(String alias)
    {
        _alias = alias;
    }    

    /* for debugging */
    protected void setSavedName(String name)
    {
        _savedName = name;
    }

    void setQuery(Query query) // reparent the relation
    {
        _query = query;
    }

    public QuerySchema getSchema()
    {
        return _schema;
    }

    public List<QueryException> getParseErrors()
    {
        return _query.getParseErrors();
    }
    

    abstract void declareFields();

    abstract TableInfo getTableInfo();

    /**
     * Return a list all the columns it is possible to select from this relation, NOT including lookup columns
     * These are the columns that will be returned by SELECT *
     * @return
     */
    abstract protected Map<String,RelationColumn> getAllColumns();

    abstract @Nullable RelationColumn getColumn(@NotNull String name);

    abstract int getSelectedColumnCount();

    /** In general we want to push lookups down as far as possible in the tree.  Sometimes this is not possible and
     * these methods may return null.  Then the caller should try parent.getLookupColumn()
     */
    abstract @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name);
    abstract @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull ColumnType.Fk fk, @NotNull String name);

    /** generate server SQL */
    public abstract SQLFragment getSql();

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

    /** used w/ Query.setRootTable(), generate a labkey SQL */
    abstract String getQueryText();


    public String getAlias()
    {
        return _alias;
    }
    

    /** declare that this FieldKey is referenced somewhere in this query (or subquery) */
    protected RelationColumn declareField(FieldKey key, QExpr location)
    {
        if (_parent != null && !_inFromClause)
            return _parent.declareField(key, location);
        return null;
    }


    /** a QField wraps a reference to a QueryRelation and a field name */
    protected QField getField(FieldKey key, QNode expr, Object referant)
    {
        if (_parent != null && !_inFromClause)
            return _parent.getField(key, expr, referant);
        return new QField(null, key.getName(), expr);
    }


    protected int getNestingLevel()
    {
        if (_parent == null)
            return 0;
        return _parent.getNestingLevel() + 1;
    }

    public abstract void setContainerFilter(ContainerFilter containerFilter);

    public abstract Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected);


    public FieldKey getContainerFieldKey()
    {
        RelationColumn col = getColumn("container");
        if (col == null)
            col = getColumn("folder");
        return col != null ? col.getFieldKey() : null;
    }

    /**
     * Why RelationColumn??
     * yes, it is similiar to ColumnInfo and I might have been able to make that work.  However,
     * ColumnInfo's belong to TableInfo (not QueryRelation) and TableInfo's are not mutable,
     * I suppose I could have created a mutable TableInfo subclass, and wrapped schema tableinfos
     * with QueryTableInfo's etc... But I didn't. I have a light-weight class that wraps
     * schema ColumnInfo's and represents internal uses of columns
     */
    public abstract static class RelationColumn
    {
        public abstract FieldKey getFieldKey();     // field key does NOT include table name/alias
        abstract String getAlias();
        abstract QueryRelation getTable();

        @NotNull
        public abstract JdbcType getJdbcType();

        public SQLFragment getValueSql()
        {
            assert ref.count() > 0;
            String tableName = getTable().getAlias();
            String columnName = getDialect(this).makeLegalIdentifier(getAlias());
            return new SQLFragment(tableName + "." + columnName);
        }

        abstract void copyColumnAttributesTo(ColumnInfo to);

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
            return ref.increment(refer);
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
    }


    // like an AliasedColumnInfo
    static public class RelationColumnInfo extends ColumnInfo
    {
        RelationColumn _column;

        public RelationColumnInfo(TableInfo parent, RelationColumn column)
        {
            super(column.getFieldKey(), parent);
            setAlias(column.getAlias());
            column.copyColumnAttributesTo(this);
            column.addRef(this);
            _column = column;
        }

        public SQLFragment getValueSql(String tableAlias)
        {
            return new SQLFragment(tableAlias + "." + getParentTable().getSqlDialect().makeLegalIdentifier(getAlias()));
        }
    }
}
