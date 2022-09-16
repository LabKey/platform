package org.labkey.query.sql;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Filter;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SelectQueryAuditProvider;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.data.xml.ColumnType;
import org.labkey.query.QueryServiceImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;


/**
 * This class is very similar to QuerySelect, it generates a SELECT/FROM/WHERE/ORDER BY sql query.  However, instead
 * of being constructed from a SQL parse tree, it is constructed by components provided by (usually) a QueryView.
 * This is meant to mimic and replace the functionality of QueryServiceImpl.getSelectSQL()
 *
 * I did not call this class "QueryView" because we already have a "QueryView"!
 *
 * NOTE: one oddness is that a list of ColumnInfo objects is passed in. This is because of how DataRegion/QueryView
 * have always worked. See QueryServiceImpl.getColumns() and RenderContext.getSelectColumns()
 *
 * We can still implement getTableInfo etc, but we probably want to preserve the aliases of the initial ColumnInfo objects.
 *
 * IDEAS:
 * a) flush out full QueryRelation implementation to make it possible to use a saved queryview in the FROM of another query
 * b) write a CustomView object constructor?
 */

public class QuerySelectView extends QueryRelation
{
    final AliasManager aliasManager;
    final TableInfo table;
    final QueryRelation from;
    final LinkedHashMap<FieldKey, ColumnInfo> selectColumns = new LinkedHashMap<>();
    final Filter filter;
    final Sort sort;
    final int maxRows;
    final long offset;
    final boolean forceSort;
    final QueryLogging queryLogging;


    public static QuerySelectView create(Query query, TableInfo table, @Nullable Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                           int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging)
    {
        if (null == selectColumns)
            selectColumns = table.getColumns();
        return new QuerySelectView(query, table.getUserSchema(), table, selectColumns, filter, sort, maxRows, offset, forceSort, queryLogging);
    }


    QuerySelectView(Query query, QuerySchema schema, TableInfo table, Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                    int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging)
    {
        super(query, schema, "QueryView");
        this.aliasManager = new AliasManager(table, selectColumns);
        this.table = table;
        this.from = new QueryTable(query, schema, table, "QueryView_from");
        if (null != selectColumns)
            selectColumns.forEach(c -> this.selectColumns.put(c.getFieldKey(), c));
        this.filter = filter;
        this.sort = sort;
        this.maxRows = maxRows;
        this.offset = offset;
        this.forceSort = forceSort;
        this.queryLogging = queryLogging;
    }


    @Override
    public void declareFields()
    {

    }


    @Override
    protected void resolveFields()
    {

    }

    @Override
    public TableInfo getTableInfo()
    {
        return null;
    }

    @Override
    protected Map<String, RelationColumn> getAllColumns()
    {
        return null;
    }

    @Override
    @Nullable RelationColumn getColumn(@NotNull String name)
    {
        return null;
    }

    @Override
    int getSelectedColumnCount()
    {
        return 0;
    }

    @Override
    @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
    {
        return null;
    }

    @Override
    @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, ColumnType.@NotNull Fk fk, @NotNull String name)
    {
        return null;
    }

    @Override
    public SQLFragment getSql()
    {
        return getSelectSQL(table, selectColumns.values(), filter, sort, maxRows, offset, forceSort, queryLogging);
    }

    @Override
    String getQueryText()
    {
        return null;
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {

    }

    @Override
    protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        return null;
    }



    /*
     * This code used to live in QueryService.getSelectSQL().  That code is still public and supported, but eventually
     * calls this implementation.
     */
    private SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                                    int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging)
    {
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for rowCount; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        QueryProfiler.getInstance().ensureListenerEnvironment();

        if (null == selectColumns)
            selectColumns = table.getColumns();
        Set<ColumnInfo> allInvolvedColumns = table.getAllInvolvedColumns(selectColumns);

        // Check incoming columns to ensure they come from table
        assert Table.checkAllColumns(table, selectColumns, "getSelectSQL() selectColumns", true);

        // Create a default sort before ensuring required columns
        // Don't add a sort if we're running a custom query and it has its own ORDER BY clause
        boolean viewHasSort = sort != null && !sort.getSortList().isEmpty();
        boolean viewHasLimit = maxRows > 0 || offset > 0 || Table.NO_ROWS == maxRows;
        SqlDialect dialect = table.getSqlDialect();

        if (viewHasLimit || forceSort)
        {
            if (!viewHasSort)
            {
                List<Sort.SortField> querySortFields = table.getSortFields();
                sort = new Sort();
                for (var sf : querySortFields)
                {
                    ColumnInfo sc = table.getColumn(sf.getFieldKey());
                    if (null != sc)
                        sort.appendSortColumn(sc.getFieldKey(), sf.getSortDirection(), false);
                }
            }

            appendDefaultSort(sort, selectColumns);
        }

        Map<String, SQLFragment> joins = new LinkedHashMap<>();
        List<ColumnInfo> allColumns = new ArrayList<>(selectColumns);
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<>();

        allColumns = QueryServiceImpl.get().ensureRequiredColumns(table, allColumns, filter, sort, null, columnMap, allInvolvedColumns);

        // Check allInvolved columns for which need to be logged
        // Logged columns may also require data logging (e.g. a patientId)
        // If a data logging column cannot be found, we will only disallow this query (throw an exception)
        //      if the logged column that requires data logging is in allColumns (which is selected columns plus ones needed for sort/filter)
        Map<ColumnInfo, Set<FieldKey>> shouldLogNameToDataLoggingMap = new HashMap<>();
        Set<ColumnLogging> shouldLogNameLoggings = new HashSet<>();
        String columnLoggingComment = null;
        SelectQueryAuditProvider selectQueryAuditProvider = null;
        for (ColumnInfo column : allInvolvedColumns)
        {
            if (!(column instanceof LookupColumn))
            {
                ColumnLogging columnLogging = column.getColumnLogging();
                if (columnLogging.shouldLogName())
                {
                    shouldLogNameLoggings.add(columnLogging);
                    if (!shouldLogNameToDataLoggingMap.containsKey(column))
                        shouldLogNameToDataLoggingMap.put(column, new HashSet<>());
                    shouldLogNameToDataLoggingMap.get(column).addAll(columnLogging.getDataLoggingColumns());
                    if (null == columnLoggingComment)
                        columnLoggingComment = columnLogging.getLoggingComment();
                    if (null == selectQueryAuditProvider)
                        selectQueryAuditProvider = columnLogging.getSelectQueryAuditProvider();
                }
            }
        }

        Set<ColumnInfo> dataLoggingColumns = new HashSet<>();
        Set<ColumnInfo> extraSelectDataLoggingColumns = new HashSet<>();
        for (Map.Entry<ColumnInfo, Set<FieldKey>> shouldLogNameToDataLoggingMapEntry : shouldLogNameToDataLoggingMap.entrySet())
        {
            for (FieldKey fieldKey : shouldLogNameToDataLoggingMapEntry.getValue())
            {
                ColumnInfo loggingColumn = columnMap.get(fieldKey);                 // Look in columnMap
                if (null == loggingColumn)
                    loggingColumn = getColumnForDataLogging(table, fieldKey);       // Look in table columns

                if (null == loggingColumn)
                {
                    AliasManager manager = new AliasManager(table, allColumns);     // Try to construct column for fieldKey
                    loggingColumn = QueryServiceImpl.get().getColumn(manager, table, columnMap, fieldKey);
                }

                if (null != loggingColumn)
                {
                    // For the case where we had to add the MRN column in Visualization, that column is in the table.columnMap, but not the local columnMap.
                    // This is because it's marked as hidden in the queryDef, so not to display, but isn't a normal "extra" column that DataRegion deems to add.
                    if (!allColumns.contains(loggingColumn))
                    {
                        allColumns.add(loggingColumn);
                        extraSelectDataLoggingColumns.add(loggingColumn);
                    }
                    if (!columnMap.containsKey(fieldKey))
                        columnMap.put(fieldKey, loggingColumn);
                    dataLoggingColumns.add(loggingColumn);
                }
                else
                {
                    // Looking for matching column in allColumns; must match by ColumnLogging object, which gets propagated up sql parse tree
                    for (ColumnInfo column : allColumns)
                        if (shouldLogNameToDataLoggingMapEntry.getKey().getColumnLogging().equals(column.getColumnLogging()))
                            queryLogging.setExceptionToThrowIfLoggingIsEnabled(new UnauthorizedException("Unable to locate required logging column '" + fieldKey.toString() + "'."));
                }
            }
        }

        if (null != table.getUserSchema() && !queryLogging.isReadOnly())
            queryLogging.setQueryLogging(table.getUserSchema().getUser(), table.getUserSchema().getContainer(), columnLoggingComment,
                    shouldLogNameLoggings, dataLoggingColumns, selectQueryAuditProvider);
        else if (!shouldLogNameLoggings.isEmpty())
            queryLogging.setExceptionToThrowIfLoggingIsEnabled(new UnauthorizedException("Column logging is required but cannot set query logging object."));

        // Check columns again: ensureRequiredColumns() may have added new columns
        assert Table.checkAllColumns(table, allColumns, "getSelectSQL() results of ensureRequiredColumns()", true);

        // I think this is for some custom filter/sorts that do not declare fields, but assume all table fields are available
        for (ColumnInfo c : table.getColumns())
        {
            if (!columnMap.containsKey(c.getFieldKey()))
                columnMap.put(c.getFieldKey(), c);
        }

        boolean requiresExtraColumns = allColumns.size() > selectColumns.size();
        SQLFragment outerSelect = new SQLFragment("SELECT *");
        SQLFragment selectFrag = new SQLFragment("SELECT ");   // SAS/SHARE JDBC driver requires "SELECT " ("SELECT\n" is not allowed), #17168
        String strComma = "\n";
        String tableName = table.getName();

        if (tableName == null)
        {
            // This shouldn't happen, but if it's null we'll blow up later without enough context to give a good error
            // message
            throw new NullPointerException("Null table name from " + table);
        }

        String tableAlias = AliasManager.makeLegalName(tableName, table.getSchema().getSqlDialect());

        if (allColumns.isEmpty())
        {
            selectFrag.append("* ");
        }
        else
        {
            CaseInsensitiveHashMap<ColumnInfo> aliases = new CaseInsensitiveHashMap<>();
            ColumnInfo prev;
            for (ColumnInfo column : allColumns)
            {
                if (null != (prev = aliases.put(column.getAlias(), column)))
                {
                    if (prev != column)
                        ExceptionUtil.logExceptionToMothership(null, new Exception("Duplicate alias in column list: " + table.getSchema() + "." + table.getName() + "." + column.getFieldKey().toSQLString() + " as " + column.getAlias()));
                    continue;
                }
                column.declareJoins(tableAlias, joins);
                selectFrag.append(strComma);
                selectFrag.append(column.getValueSql(tableAlias));
                selectFrag.append(" AS ");
                selectFrag.append(dialect.makeLegalIdentifier(column.getAlias()));
                strComma = ",\n";
            }
        }

        if (requiresExtraColumns)
        {
            outerSelect = new SQLFragment("SELECT ");
            strComma = "";

            for (ColumnInfo column : selectColumns)
            {
                outerSelect.append(strComma);
                outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                strComma = ", ";
            }
            for (ColumnInfo column : extraSelectDataLoggingColumns)
            {
                outerSelect.append(strComma);
                outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                strComma = ", ";
            }
        }

        SQLFragment fromFrag = new SQLFragment("FROM ");
        Set<FieldKey> fieldKeySet = new TreeSet<>();
        allColumns.stream()
                .map(col -> col instanceof WrappedColumn ? ((WrappedColumn) col).getWrappedColumn() : col)
                .forEach(col -> {
                    var fk = col.getFieldKey();
                    fieldKeySet.add(fk);
                    if (null != fk.getParent())
                        fieldKeySet.add(fk.getRootFieldKey());
                });
        SQLFragment getFromSql = table.getFromSQL(tableAlias, fieldKeySet);
        fromFrag.append(getFromSql);
        fromFrag.append(" ");

        for (Map.Entry<String, SQLFragment> entry : joins.entrySet())
        {
            fromFrag.append("\n").append(entry.getValue());
        }

        SQLFragment filterFrag = null;

        if (filter != null)
        {
            if (filter instanceof SimpleFilter)
            {
                for (var c : ((SimpleFilter) filter).getClauses())
                    if (c instanceof QueryServiceImpl.QueryCompareClause qcc)
                        qcc.setQuery(_query);
            }
            filterFrag = filter.getSQLFragment(dialect, "x", columnMap);
        }

        String orderBy = null;

        if (sort != null)
        {
            orderBy = sort.getOrderByClause(dialect, columnMap);
        }

        if ((filterFrag == null || filterFrag.getSQL().length() == 0) && sort == null && Table.ALL_ROWS == maxRows && offset == 0)
        {
            selectFrag.append("\n").append(fromFrag);
            return selectFrag;
        }

        SQLFragment nestedFrom = new SQLFragment();
        nestedFrom.append("FROM (\n").append(selectFrag).append("\n").append(fromFrag).append(") x");
        SQLFragment ret = dialect.limitRows(outerSelect, nestedFrom, filterFrag, orderBy, null, maxRows, offset);

        if (AppProps.getInstance().isDevMode())
        {
            SQLFragment t = new SQLFragment();
            t.appendComment("<QueryServiceImpl.getSelectSQL(" + AliasManager.makeLegalName(table.getName(), dialect) + ")>", dialect);
            t.append(ret);
            t.appendComment("</QueryServiceImpl.getSelectSQL()>", dialect);
            ret = SQLFragment.prettyPrint(t);
        }

        return ret;
    }


    /* create (or append) fields to sort in attempt to create a stable sort for paging */
    private static Sort appendDefaultSort(Sort sort, Collection<ColumnInfo> columns)
    {
        // try to add key columns to sort
        if (!addSortableColumns(sort, columns, true))
        {
            // if that fails and the sort is empty, add more fields
            if (sort.getSortList().isEmpty())
                addSortableColumns(sort, columns, false);
        }
        return sort;
    }


    /* see if columns in column.getSortFieldKeys() are resolvable, so we avoid proposing a default sort that won't work */
    private static List<ColumnInfo> resolveSortFieldKeys(ColumnInfo col, Map<FieldKey, ColumnInfo> selectColumns)
    {
        List<FieldKey> sortFieldKeys = col.getSortFieldKeys();
        if (null != sortFieldKeys && !sortFieldKeys.isEmpty())
        {
            // fast way: see if all columns are already selected
            if (sortFieldKeys.stream().allMatch(f -> null != selectColumns.get(f)))
                return sortFieldKeys.stream().map(selectColumns::get).collect(Collectors.toList());
            // slow way: use QueryService
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(col.getParentTable(), sortFieldKeys);
            if (columns.size() == sortFieldKeys.size() && !columns.containsValue(null))
                return sortFieldKeys.stream().map(columns::get).collect(Collectors.toList());
        }

        if (!col.isSortable())
            return null;
        return Collections.singletonList(col);
    }


    private static boolean addSortableColumns(Sort sort, Collection<ColumnInfo> columns, boolean usePrimaryKey)
    {
	    /* There is a bit of a chicken-and-egg problem here
	        we need to know what we want to sort on before calling ensureRequiredColumns, but we don't know for sure we
	        which columns we can sort on until we validate which columns are available (because of getSortFieldKeys)
	     */
        Map<FieldKey, ColumnInfo> available = new HashMap<>();
        columns.forEach(c -> available.putIfAbsent(c.getFieldKey(), c));

        Set<FieldKey> presentInSort = sort.getSortList().stream().map(sf -> sf.getFieldKey()).collect(Collectors.toSet());

        boolean addedSortKeys = false;

        for (ColumnInfo column : columns)
        {
            if (usePrimaryKey && !column.isKeyField())
                continue;
            List<ColumnInfo> sortFields = resolveSortFieldKeys(column, available);
            if (sortFields != null && !sortFields.isEmpty())
            {
                if (presentInSort.add(column.getFieldKey()))
                    // NOTE: we don't need to expando the list here, Sort.getOrderByClause() will do that
                    sort.appendSortColumn(column.getFieldKey(), column.getSortDirection(), false);
                addedSortKeys = true;
            }
        }
        return addedSortKeys;
    }


    @Nullable
    private static ColumnInfo getColumnForDataLogging(TableInfo table, FieldKey fieldKey)
    {
        ColumnInfo loggingColumn = table.getColumn(fieldKey);
        if (null != loggingColumn)
            return loggingColumn;

        // Column names may be mangled by visualization; lookup by original column name
        for (ColumnInfo column : table.getColumns())
        {
            if (column.getColumnLogging().getOriginalColumnFieldKey().equals(fieldKey))
                return column;
        }
        return null;
    }

}
