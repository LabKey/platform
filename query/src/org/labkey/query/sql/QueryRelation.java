package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.data.xml.ColumnType;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface QueryRelation
{
    default String getDebugName()
    {
        return getClass().getSimpleName() + "@" + System.identityHashCode(this) + " " + getAlias();
    }

    void setQuery(Query q);

    void setParent(QueryRelation parent);

    void setAlias(String alias);

    QuerySchema getSchema();

    List<QueryException> getParseErrors();

    void reportWarning(String string, @Nullable QNode node);

    void declareFields();

    /**
     * actually bind all field references
     */
    void resolveFields();

    /* public for testing only */
    TableInfo getTableInfo();

    /**
     * Return a list all the columns it is possible to select from this relation, NOT including lookup columns
     * These are the columns that will be returned by SELECT *
     *
     * @return
     */
    Map<String, AbstractQueryRelation.RelationColumn> getAllColumns();

    @Nullable AbstractQueryRelation.RelationColumn getFirstColumn();

    @Nullable AbstractQueryRelation.RelationColumn getColumn(@NotNull String name);

    Collection<String> getKeyColumns();

    int getSelectedColumnCount();

    /**
     * In general we want to push lookups down as far as possible in the tree.  Sometimes this is not possible and
     * these methods may return null.  Then the caller should try parent.getLookupColumn()
     */
    @Nullable AbstractQueryRelation.RelationColumn getLookupColumn(@NotNull AbstractQueryRelation.RelationColumn parent, @NotNull String name);

    @Nullable AbstractQueryRelation.RelationColumn getLookupColumn(@NotNull AbstractQueryRelation.RelationColumn parent, @NotNull ColumnType.Fk fk, @NotNull String name);

    /**
     * generate server SQL
     */
    SQLFragment getSql();

    SQLFragment getFromSql();

    /**
     * used w/ Query.setRootTable(), generate a labkey SQL
     */
    String
    getQueryText();

    String getAlias();

    AbstractQueryRelation.RelationColumn declareField(FieldKey key, QExpr location);

    QField getField(FieldKey key, QNode expr, Object referant);

    MethodInfo getMethod(String name);

    int getNestingLevel();

    void setContainerFilter(ContainerFilter containerFilter);

    //NOTE: column order is important when generating the suggested column list
    //subclasses should implement getSuggestedColumns() instead of overriding this
    Set<AbstractQueryRelation.RelationColumn> getOrderedSuggestedColumns(Set<AbstractQueryRelation.RelationColumn> selected);

    Set<AbstractQueryRelation.RelationColumn> getSuggestedColumns(Set<AbstractQueryRelation.RelationColumn> selected);

    FieldKey getContainerFieldKey();

    Set<SchemaKey> getResolvedTables();

    CommonTableExpressions getCommonTableExpressions();

    void setCommonTableExpressions(CommonTableExpressions queryWith);

    String toStringDebug();

    List<Sort.SortField> getSortFields();

    void setSavedName(String name);

    /**
     * Some relations contribute new table columns to the query result.  For instance, QueryTable and QueryLookupWrapper both
     * resolve column from tables in the UserSchema.
     * <p>
     * In addition, QueryUnion is a special case because none of the columns directly pass through to the underlying tables,
     * they all are mashup of a group of columns/expressions.  So UNION queries bascically create a new "namespace" of columns
     * and act like a leaf node or virtual table the relations above.
     * <p>
     * Relations that are marked with the ColumnResolverRelation interface participate in the "remapFieldKey()" process and in
     * generating correct ColumnLogging objects.
     * <p>
     * All columns returned from gatherInvolvedSelectColumns() should be owned by a Relation that implements ColumnResolvingRelation.
     */
    interface ColumnResolvingRelation
    {
        Map<FieldKey, FieldKey> getRemapMap(Map<String,FieldKey> outerMap);
    }


    // This is a helper method to create a map from column names in one scope (e.g QueryTable) to those columns found in
    // an outer scope (e.g. query output columns).  See QueryTableInfo.  The inner table provides a map from column FieldKeys to
    // column unique names.  Another map from uniquename to String is also provided.  This method returns a virtual Map from one to the other.
    static Map<FieldKey, FieldKey> generateRemapMap(Map<FieldKey, String> innerMap, Map<String, FieldKey> outerMap)
    {
        return new AbstractMap<>()
        {
            @NotNull
            @Override
            public Set<Entry<FieldKey, FieldKey>> entrySet()
            {
                throw new IllegalStateException();
            }

            @Override
            public FieldKey get(Object key)
            {
                return outerMap.get(innerMap.get(key));
            }

            @Override
            public FieldKey put(FieldKey key, FieldKey value)
            {
                throw new IllegalStateException();
            }

            @Override
            public boolean isEmpty()
            {
                return innerMap.isEmpty() || outerMap.isEmpty();
            }
        };
    }
}