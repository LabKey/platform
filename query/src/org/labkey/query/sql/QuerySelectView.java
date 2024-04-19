package org.labkey.query.sql;


import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Filter;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SelectQueryAuditProvider;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.data.dialect.SqlDialect;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;


/**
 * This class is very similar to QuerySelect, it generates a SELECT/FROM/WHERE/ORDER BY sql query.  However, instead
 * of being constructed from a SQL parse tree, it is constructed by components provided by (usually) a QueryView.
 * This is meant to mimic and replace the functionality of QueryServiceImpl.getSelectSQL()
 * <p>
 * I did not call this class "QueryView" because we already have a "QueryView"!
 * <p>
 * NOTE: one oddness is that a list of ColumnInfo objects is passed in. This is because of how DataRegion/QueryView
 * have always worked. See QueryServiceImpl.getColumns() and RenderContext.getSelectColumns()
 * <p>
 * We can still implement getTableInfo etc, but we probably want to preserve the aliases of the initial ColumnInfo objects.
 * <p>
 * IDEAS:
 * a) flush out full QueryRelation implementation to make it possible to use a saved queryview in the FROM of another query
 * b) write a CustomView object constructor?
 */

public class QuerySelectView extends AbstractQueryRelation
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
    final boolean distinct;

    public static QuerySelectView create(Query query, TableInfo table, @Nullable Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                                         int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging, boolean distinct)
    {
        if (null == selectColumns)
            selectColumns = table.getColumns();
        return new QuerySelectView(query, table.getUserSchema(), table, selectColumns, filter, sort, maxRows, offset, forceSort, queryLogging, distinct);
    }

    QuerySelectView(Query query, QuerySchema schema, TableInfo table, Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                    int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging, boolean distinct)
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
        this.distinct = distinct;
    }


    @Override
    public void declareFields()
    {

    }


    @Override
    public void resolveFields()
    {

    }

    @Override
    public TableInfo getTableInfo()
    {
        return null;
    }

    @Override
    public Map<String, RelationColumn> getAllColumns()
    {
        return null;
    }

    @Override
    public @Nullable AbstractQueryRelation.RelationColumn getFirstColumn()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    public RelationColumn getColumn(@NotNull String name)
    {
        return null;
    }

    @Override
    public int getSelectedColumnCount()
    {
        return 0;
    }

    @Override
    @Nullable
    public RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
    {
        return null;
    }

    @Override
    @Nullable
    public RelationColumn getLookupColumn(@NotNull RelationColumn parent, ColumnType.@NotNull Fk fk, @NotNull String name)
    {
        return null;
    }

    @Override
    public SQLFragment getSql()
    {
        return getSelectSQL(table, selectColumns.values(), filter, sort, maxRows, offset, forceSort, queryLogging, distinct);
    }

    @Override
    public String getQueryText()
    {
        return null;
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {

    }

    @Override
    public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        return null;
    }



    /*
     * This code used to live in QueryService.getSelectSQL().  That code is still public and supported, but eventually
     * calls this implementation.
     */
    private SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                                    int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging, boolean distinct)
    {
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for rowCount; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        if (null == selectColumns)
            selectColumns = table.getColumns();

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

        allColumns = QueryServiceImpl.get().ensureRequiredColumns(table, allColumns, filter, sort, null, columnMap);

        Set<ColumnInfo> extraSelectDataLoggingColumns = getRequiredDataLoggingColumns(table, queryLogging, allColumns, columnMap);

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
                selectFrag.appendIdentifier(dialect.makeLegalIdentifier(column.getAlias()));
                strComma = ",\n";
            }
        }

        if (requiresExtraColumns || distinct)
        {
            outerSelect = distinct ?   new SQLFragment("SELECT DISTINCT ") : new SQLFragment("SELECT ");
            strComma = "";

            for (ColumnInfo column : selectColumns)
            {
                outerSelect.append(strComma);
                outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                strComma = ", ";
            }

            // NOTE: I think extraSelectDataLoggingColumns won't contain columns that were added by ensureRequiredColumns()
            // It is safer to recheck the entire list of columns in queryLogging.getDataLoggingColumns()
            if (distinct)
            {
                if (null == queryLogging.getExceptionToThrowIfLoggingIsEnabled() && !queryLogging.isEmpty())
                {
                    Set<FieldKey> select = selectColumns.stream().map(ColumnInfo::getFieldKey).collect(Collectors.toSet());
                    for (var required : queryLogging.getDataLoggingColumns())
                    {
                        if (!select.contains(required.getFieldKey()))
                            queryLogging.setExceptionToThrowIfLoggingIsEnabled(new UnauthorizedException("Unable to locate required logging column '" + required.getFieldKey().toString() + "'."));
                    }
                }
            }
            else
            {
                for (ColumnInfo column : extraSelectDataLoggingColumns)
                {
                    outerSelect.append(strComma);
                    outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                    strComma = ", ";
                }
            }
        }

        SQLFragment fromFrag = new SQLFragment("FROM ");
        Set<FieldKey> fieldKeySet = new TreeSet<>();
        allColumns.stream()
                .map(col -> (null != col.getWrappedColumnName() && null != table.getColumn(col.getWrappedColumnName())) ?  table.getColumn(col.getWrappedColumnName()) : col)
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
            if (filter instanceof SimpleFilter simpleFilter)
            {
                for (var c : simpleFilter.getClauses())
                {
                    if (c instanceof QueryServiceImpl.QueryCompareClause qcc)
                        qcc.setQuery(_query);
                }
            }
            filterFrag = filter.getSQLFragment(dialect, "x", columnMap);
        }

        String orderBy = null;

        if (sort != null)
        {
            orderBy = sort.getOrderByClause(dialect, columnMap);
        }

        if ((filterFrag == null || filterFrag.getSQL().length() == 0) && sort == null && Table.ALL_ROWS == maxRows && offset == 0 && !distinct)
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


    @NotNull
    private static Set<ColumnInfo> getRequiredDataLoggingColumns(TableInfo table, @NotNull QueryLogging queryLogging, List<ColumnInfo> allColumns, Map<FieldKey, ColumnInfo> columnMap)
    {
        Map<ColumnInfo, Set<FieldKey>> shouldLogNameToDataLoggingMap = new HashMap<>();
        Set<ColumnLogging> shouldLogNameLoggings = new HashSet<>();
        Set<String> columnLoggingComments = new LinkedHashSet<>();
        SelectQueryAuditProvider selectQueryAuditProvider = null;
        for (ColumnInfo column : allColumns)
        {
            ColumnLogging columnLogging = column.getColumnLogging();
            if (null == columnLogging)
                continue;
            if (columnLogging.shouldLogName())
            {
                shouldLogNameLoggings.add(columnLogging);
                if (null == selectQueryAuditProvider)
                    selectQueryAuditProvider = columnLogging.getSelectQueryAuditProvider();
                columnLoggingComments.addAll(columnLogging.getLoggingComments());
                if (null != columnLogging.getException())
                {
                    UnauthorizedException uae = columnLogging.getException();
                    // Use UnexpectedException to be consistent I guess??
                    queryLogging.setExceptionToThrowIfLoggingIsEnabled(uae);
                }
                else
                {
                    if (!shouldLogNameToDataLoggingMap.containsKey(column))
                        shouldLogNameToDataLoggingMap.put(column, new HashSet<>());
                    shouldLogNameToDataLoggingMap.get(column).addAll(columnLogging.getDataLoggingColumns());
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
                    queryLogging.setExceptionToThrowIfLoggingIsEnabled(new UnauthorizedException("Unable to locate required logging column '" + fieldKey.toString() + "'."));
                }
            }
        }

        if (null != table.getUserSchema() && !queryLogging.isReadOnly())
        {
            queryLogging.setQueryLogging(table.getUserSchema().getUser(), table.getUserSchema().getContainer(), StringUtils.join(columnLoggingComments,"\n"),
                    shouldLogNameLoggings, dataLoggingColumns, selectQueryAuditProvider);
        }
        else if (!shouldLogNameLoggings.isEmpty())
        {
            queryLogging.setExceptionToThrowIfLoggingIsEnabled(new UnauthorizedException("Column logging is required but cannot set query logging object."));
        }
        return extraSelectDataLoggingColumns;
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

    private static final int DEFAULT_SORT_MAX_COLUMNS = 5; // Limit default ORDER BY in no PK case to five columns

    private static boolean addSortableColumns(Sort sort, Collection<ColumnInfo> columns, boolean usePrimaryKey)
    {
	    /* There is a bit of a chicken-and-egg problem here
	        we need to know what we want to sort on before calling ensureRequiredColumns, but we don't know for sure
	        which columns we can sort on until we validate which columns are available (because of getSortFieldKeys)
	     */
        Map<FieldKey, ColumnInfo> available = new HashMap<>();
        columns.forEach(c -> available.putIfAbsent(c.getFieldKey(), c));

        Set<FieldKey> presentInSort = sort.getSortList().stream().map(Sort.SortField::getFieldKey).collect(Collectors.toSet());

        boolean addedSortKeys = false;

        for (ColumnInfo column : columns)
        {
            if (usePrimaryKey && !column.isKeyField())
                continue;
            List<ColumnInfo> sortFields = resolveSortFieldKeys(column, available);
            if (sortFields != null && !sortFields.isEmpty())
            {
                if (presentInSort.add(column.getFieldKey()))
                {
                    // NOTE: we don't need to expando the list here, Sort.getOrderByClause() will do that
                    sort.appendSortColumn(column.getFieldKey(), column.getSortDirection(), false);
                    addedSortKeys = true;
                    if (sort.getSortList().size() >= DEFAULT_SORT_MAX_COLUMNS)
                        break;
                }
            }
        }
        return addedSortKeys;
    }
}
