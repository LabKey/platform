/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.collections.RowMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.element.CsrfInput;
import org.labkey.api.util.element.Input.InputBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.PopupMenuView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.visualization.VisualizationUrls;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.util.DOM.Attribute.colspan;
import static org.labkey.api.util.DOM.Attribute.id;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.EM;
import static org.labkey.api.util.DOM.SCRIPT;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;

/** Shared across a variety of different views of a TableInfo, such as grid, details, insert, and update. Knows
 * about buttons that might appear in the view, the columns to be shown, etc. */
public class DataRegion extends DisplayElement
{
    private static final Logger _log = LogManager.getLogger(DataRegion.class);
    private static final String[] HIDDEN_FILTER_COLUMN_SUFFIXES = {"RowId", "DisplayName", "Description", "Label", "Caption", "Value"};
    private static final String TOGGLE_CHECKBOX_NAME = ".toggle";

    // TODO: Ever hear of an enum?
    public static final int MODE_NONE = 0;
    public static final int MODE_INSERT = 1;
    public static final int MODE_UPDATE = 2;
    public static final int MODE_GRID = 4;
    public static final int MODE_DETAILS = 8;
    public static final int MODE_UPDATE_MULTIPLE = 16;

    public static final String LAST_FILTER_PARAM = ".lastFilter";
    public static final String SELECT_CHECKBOX_NAME = ".select";
    public static final String OLD_VALUES_NAME = ".oldValues";
    public static final String CONTAINER_FILTER_NAME = ".containerFilterName";

    public static final String DEFAULTTIME = "Time";
    public static final String DEFAULTDATE = "Date";
    public static final String DEFAULTDATETIME = "DateTime";

    public static final String EXPERIMENTAL_DATA_REGION_ASYNC_TOTAL_ROWS = "dataregionAsyncTotalRows";

    private final String _domId = "lk-region-" + UniqueID.getServerSessionScopedUID(); // TODO: Consider using UniqueID.getRequestScopedUID(request) instead
    private final List<FormField> _hiddenFormFields = new ArrayList<>();   // Hidden params to be posted (e.g., to pass a query string along with selected grid rows)
    private final List<ButtonBarConfig> _buttonBarConfigs = new ArrayList<>();
    private final List<ContextAction> _contextActions = new ArrayList<>();
    private final List<ContextAction> _viewActions = new ArrayList<>();
    private final List<MessageSupplier> _messageSuppliers = new ArrayList<>();
    private final List<GroupTable> _groupTables = new ArrayList<>();

    private String _name = null;
    private QuerySettings _settings = null;
    private boolean _allowHeaderLock = true;
    private List<DisplayColumn> _displayColumns = new ArrayList<>();
    private Map<String, List<Aggregate.Result>> _aggregateResults = null;
    private AggregateRowConfig _aggregateRowConfig = new AggregateRowConfig();
    private TableInfo _table = null;
    private ActionURL _selectAllURL = null;
    private boolean _showRecordSelectors = false;
    private boolean _showFilters = true;
    private boolean _sortable = true;
    private boolean _showFilterDescription = true;
    private ButtonBar _gridButtonBar = new ButtonBar();
    private ButtonBar _insertButtonBar = new ButtonBar();
    private ButtonBar _updateButtonBar = new ButtonBar();
    private ButtonBar _detailsButtonBar = new ButtonBar();
    private List<String> _recordSelectorValueColumns;
    private int _maxRows = Table.ALL_ROWS;   // Display all rows by default
    private ButtonBarPosition _buttonBarPosition = ButtonBarPosition.TOP;
    private boolean allowAsync = false;
    private ActionURL _formActionUrl = null;
    private HtmlString _noRowsMessage = HtmlString.of("No data to show.");
    private boolean _shadeAlternatingRows = true;
    private boolean _showBorders = true;
    private boolean _showSurroundingBorder = true;
    private boolean _showPagination = true;
    private boolean _showPaginationCount = true;
    private boolean _horizontalGroups = true;
    private boolean _errorCreatingResults = false;
    private Long _totalRows = null; // total rows in the query or null if unknown
    private Integer _rowCount = null; // number of rows in the result set or null if unknown
    private boolean _complete = false; // true if all rows are in the ResultSet
    private boolean _buttonBarRendered = false;
    private List<Message> _messages;

    protected boolean _showSelectMessage = true;

    private static class GroupTable
    {
        private final List<DisplayColumnGroup> _groups = new ArrayList<>();
        private List<String> _groupHeadings = new ArrayList<>();

        public List<DisplayColumnGroup> getGroups()
        {
            return _groups;
        }

        public List<String> getGroupHeadings()
        {
            return _groupHeadings;
        }

        public void setGroupHeadings(List<String> groupHeadings)
        {
            _groupHeadings = groupHeadings;
        }
    }

    record FormField(String name, String value) {}

    /**
     * Messages that are displayed to the user and included in Query API responses.
     * These messages' content should NOT be HTML encoded as the responsibility
     * for encoding is left to the caller.
     * See Issue #42017
     */
    public static class Message
    {
        private final String _area;
        private final String _content;
        private final MessageType _type;

        public Message(String content, MessageType type, String area)
        {
            _area = area;
            _content = content;
            _type = type;
        }

        public Message(String content, MessageType type, MessagePart area)
        {
            this(content, type, area != null ? area.name() : null);
        }

        public String getArea()
        {
            return _area;
        }

        /** Caller is responsible for HTML encoding. */
        public String getContent()
        {
            return _content;
        }

        public MessageType getType()
        {
            return _type;
        }
    }

    public enum MessagePart
    {
        view,
        filter,
        header,
    }

    public enum MessageType
    {
        ERROR,
        INFO,
        WARNING
    }

    /**
     * Interface to allow late bound addition of messages to this data region, the supplier
     * will be invoked at render time when the result set (if any) has been created. A supplier can
     * be added using the addMessageSupplier method
     */
    public interface MessageSupplier
    {
        List<Message> getMessages(DataRegion dataRegion);
    }

    protected void addMessage(Message message)
    {
        if (_messages == null)
            _messages = new ArrayList<>();

        if (null != message)
            _messages.add(message);
    }

    @Nullable
    public List<Message> getMessages()
    {
        return _messages;
    }

    public void addMessageSupplier(MessageSupplier supplier)
    {
        _messageSuppliers.add(supplier);
    }

    public void addDisplayColumn(@NotNull  DisplayColumn col)
    {
        assert null != col;
        if (null == col)
            return;
        _displayColumns.add(col);
    }

    public void addDisplayColumn(int index, @NotNull DisplayColumn col)
    {
        assert null != col;
        if (null == col)
            return;
        _displayColumns.add(index, col);
    }

    /* We don't want callers to modify this list directly.  However, this is the only way for subclasses to modify the list */
    public List<DisplayColumn> getDisplayColumns()
    {
        return _displayColumns;
    }

    public DisplayColumn getDisplayColumn(int i)
    {
        return _displayColumns.get(i);
    }

    public void clearColumns()
    {
        _displayColumns.clear();
    }

    public void addColumn(ColumnInfo col)
    {
        addDisplayColumn(col.getRenderer());
    }

    public void addColumn(int index, ColumnInfo col)
    {
        addDisplayColumn(index, col.getRenderer());
    }

    public void addColumns(Collection<ColumnInfo> cols)
    {
        for (ColumnInfo col : cols)
            addDisplayColumn(col.getRenderer());
    }

    public void addColumns(TableInfo tinfo, String colNames)
    {
        addColumns(tinfo.getColumns(colNames));
    }

    public List<String> getDisplayColumnNames()
    {
        List<String> list = new ArrayList<>();

        for (DisplayColumn dc : getDisplayColumns())
            list.add(dc.getName());

        return list;
    }

    public void setDisplayColumns(List<DisplayColumn> displayColumns)
    {
        /** NOTE - the cleaner thing to do here would be
         *         clearColumns();
         *         displayColumns.forEach(this::addDisplayColumn);
         * however, this breaks MS2 which seems to do funny things with nested RenderContexts
         */
        _displayColumns = displayColumns;
    }

    public void removeColumns(String... columns)
    {
        for (String column : columns)
        {
            String trimmedColName = column.trim();
            // go backwards through the list so we don't have to worry about a
            // removal changing our next index.
            for (int colIndex = _displayColumns.size() - 1; colIndex >= 0; colIndex--)
            {
                DisplayColumn dc = _displayColumns.get(colIndex);
                if (trimmedColName.equalsIgnoreCase(dc.getName()))
                    _displayColumns.remove(colIndex);
            }
        }
    }

    /* remove comma-separated string of column names from List */
    public void removeColumns(String columns)
    {
        String[] eachCol = columns.split(",");
        removeColumns(eachCol);
    }

    public void setColumns(List<ColumnInfo> cols)
    {
        clearColumns();

        for (ColumnInfo column : cols)
            addColumn(column);
    }

    // Return DisplayColumn by name (or null if no DisplayColumn has this name)
    // UNDONE: Create HashMap on first use?
    public DisplayColumn getDisplayColumn(String name)
    {
        for (DisplayColumn dc : getDisplayColumns())
        {
            if (name.equalsIgnoreCase(dc.getName()))
                return dc;
        }

        return null;
    }

    public void replaceDisplayColumn(String name, DisplayColumn replacement)
    {
        for (int i = 0; i < _displayColumns.size(); i++)
        {
            if (name.equalsIgnoreCase(_displayColumns.get(i).getName()))
            {
                _displayColumns.remove(i);
                addDisplayColumn(i, replacement);
                return;
            }
        }
    }

    public void addButtonBarConfig(ButtonBarConfig buttonBarConfig)
    {
        assert buttonBarConfig != null : "Cannot add a null ButtonBarConfig";
        _buttonBarConfigs.add(buttonBarConfig);
    }

    public void addHiddenFormField(ActionURL.Param urlParam, URLHelper url)
    {
        addHiddenFormField(urlParam.toString(), url.getLocalURIString());
    }

    public void addHiddenFormField(Enum<?> name, String value)
    {
        addHiddenFormField(name.toString(), value);
    }

    public void addHiddenFormField(String name, String value)
    {
        if (null != value)
            _hiddenFormFields.add(new FormField(name, value));
    }

    public
    @NotNull
    LinkedHashMap<FieldKey, ColumnInfo> getSelectColumns()
    {
        TableInfo table = getTable();

        // includes old DisplayColumn.addQueryColumns()
        List<ColumnInfo> originalColumns = RenderContext.getSelectColumns(getDisplayColumns(), table);

        assert Table.checkAllColumns(table, originalColumns, "DataRegion.getSelectColumns() originalColumns");

        // allow DataRegion subclass to add columns (yuck)
        LinkedHashSet<ColumnInfo> columns = new LinkedHashSet<>(originalColumns);
        addQueryColumns(columns);

        assert Table.checkAllColumns(table, columns, "DataRegion.getSelectColumns() columns");

        LinkedHashMap<FieldKey, ColumnInfo> ret = QueryService.get().getColumns(table, Collections.emptySet(), columns);

        assert Table.checkAllColumns(table, columns, "DataRegion.getSelectColumns()");

        return ret;
    }


    public void setShowRecordSelectors(boolean show)
    {
        _showRecordSelectors = show;
    }

    /**
     * Called after configuring the button bar, check if any buttons require selection (e.g., "Delete").
     */
    public boolean getShowRecordSelectors(RenderContext ctx)
    {
        // Issue 11569: QueryView.showRecordSelectors should take metadata override buttons into account
        return _showRecordSelectors || (_buttonBarPosition != ButtonBarPosition.NONE && (_gridButtonBar.isAlwaysShowRecordSelectors() || _gridButtonBar.hasRequiresSelectionButton(ctx)));
    }

    public boolean getShowSelectMessage()
    {
        return _showSelectMessage;
    }

    public void setShowSelectMessage(boolean showSelectMessage)
    {
        _showSelectMessage = showSelectMessage;
    }

    public boolean getShowFilters()
    {
        return _showFilters;
    }


    public void setShowFilters(boolean show)
    {
        _showFilters = show;
    }

    public boolean isSortable()
    {
        return _sortable;
    }

    public void setSortable(boolean sortable)
    {
        _sortable = sortable;
    }

    public boolean isShowFilterDescription()
    {
        return _showFilterDescription;
    }

    public void setShowFilterDescription(boolean showFilterDescription)
    {
        _showFilterDescription = showFilterDescription;
    }

    public ButtonBar getButtonBar(int mode)
    {
        switch (mode)
        {
            case MODE_INSERT ->
            {
                return _insertButtonBar;
            }
            case MODE_UPDATE, MODE_UPDATE_MULTIPLE ->
            {
                return _updateButtonBar;
            }
            case MODE_GRID ->
            {
                return _gridButtonBar;
            }
            case MODE_DETAILS ->
            {
                return _detailsButtonBar;
            }
            default ->
            {
                _log.error("getting button bar for non existent mode");
                return null;
            }
        }
    }

    private List<String> getButtonBarOnRenders()
    {
        return _buttonBarConfigs.stream()
            .map(ButtonBarConfig::getOnRenderScript)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public void setButtonBar(ButtonBar buttonBar)
    {
        _insertButtonBar = _updateButtonBar = _gridButtonBar = _detailsButtonBar = buttonBar;
    }

    public void setButtonBar(ButtonBar buttonBar, int mode)
    {
        switch (mode)
        {
            case MODE_INSERT -> _insertButtonBar = buttonBar;
            case MODE_UPDATE -> _updateButtonBar = buttonBar;
            case MODE_GRID -> _gridButtonBar = buttonBar;
            case MODE_DETAILS -> _detailsButtonBar = buttonBar;
            default -> _log.error("Setting button bar for non existent mode");
        }
    }

    public void setAllowHeaderLock(boolean allow)
    {
        _allowHeaderLock = allow;
    }

    public boolean getAllowHeaderLock()
    {
        return _allowHeaderLock;
    }

    public final String getDomId()
    {
        return _domId;
    }

    public final String getFormId()
    {
        return getDomId() + "-form";
    }

    public String getName()
    {
        if (null == _name)
        {
            if (null != getSettings() && null != getSettings().getDataRegionName())
                _name = getSettings().getDataRegionName();
            else if (getTable() != null)
                _name = getTable().getName();
        }
        return _name;
    }

    /**
     * Use {@link DataRegion#setSettings(QuerySettings)} to set the name instead.
     */
    @Deprecated
    public void setName(String name)
    {
        _name = name;
    }

    public int getMaxRows()
    {
        return getSettings() != null ? getSettings().getMaxRows() : _maxRows;
    }

    /**
     * Use {@link QuerySettings#setMaxRows(int)}.
     */
    @Deprecated
    public void setMaxRows(int maxRows)
    {
        if (getSettings() != null)
            getSettings().setMaxRows(maxRows);
        else
            _maxRows = maxRows;
    }

    public long getOffset()
    {
        return getSettings() != null ? getSettings().getOffset() : 0;
    }

    public void setSettings(QuerySettings settings)
    {
        _settings = settings;
    }

    public QuerySettings getSettings()
    {
        return _settings;
    }

    public ShowRows getShowRows()
    {
        return getSettings() != null ? getSettings().getShowRows() : ShowRows.PAGINATED;
    }

    @Nullable
    public String getSelectionKey()
    {
        if (getSettings() != null && getSettings().getSelectionKey() != null)
            return getSettings().getSelectionKey();
        if (getTable() != null && getTable().getSchema() != null)
            return DataRegionSelection.getSelectionKey(getTable().getSchema().getName(), getTable().getName(), null, getName());
        return null;
    }

    // TODO: Should get rid of getTable() & setTable() and just rely on the query columns associated with each display column.
    // Also, dc.isQueryColumn() is redundant with !queryColumns.isEmpty()
    public TableInfo getTable()
    {
        if (_table != null)
            return _table;

        for (DisplayColumn dc : _displayColumns)
        {
            if (dc.isQueryColumn())
            {
                _table = dc.getColumnInfo().getParentTable();
                break;
            }
        }

        if (_table != null)
            return _table;

        // Non-query display columns can still have query column dependencies (examples: ms2 DeltaScan and Hydrophobicity columns).
        // Last attempt at finding the table: iterate through the display columns and return the parent table of the first query column dependency.
        Set<ColumnInfo> queryColumns = new HashSet<>();
        for (DisplayColumn dc : _displayColumns)
        {
            dc.addQueryColumns(queryColumns);
            if (queryColumns.contains(null))
            {
                // Catch this problem before it's too late to figure out who the culprit was
                throw new IllegalStateException("The display column " + dc + " added one or more null columns to the set of query columns");
            }

            if (!queryColumns.isEmpty())
            {
                Iterator<ColumnInfo> iter = queryColumns.iterator();
                ColumnInfo col = iter.next();
                _table = col.getParentTable();
                break;
            }
        }

        if (_table == null)
        {
            for (GroupTable groupTable : _groupTables)
            {
                for (DisplayColumnGroup group : groupTable.getGroups())
                {
                    for (DisplayColumn dc : group.getColumns())
                    {
                        if (dc.isQueryColumn())
                        {
                            _table = dc.getColumnInfo().getParentTable();
                            break;
                        }
                    }
                    if (_table != null)
                    {
                        break;
                    }
                }
            }
        }

        return _table;
    }

    public void setTable(TableInfo table)
    {
        _table = table;
    }

    protected boolean isDefaultView(RenderContext ctx)
    {
        return (ctx.getView() == null || StringUtils.isEmpty(ctx.getView().getName()));
    }

    public @NotNull Map<String, Object> getQueryParameters()
    {
        return null == getSettings() ? Collections.emptyMap() : getSettings().getQueryParameters();
    }

    /**
     * Get a Results from the DataRegion.
     * Has the side-effect of setting the Results and this DataRegion
     * on the RenderContext and selecting any aggregates
     * (including the row count aggregate, unless pagination or pagination count are false.)
     * Callers should check for ReadPermission before requesting a Results.
     *
     * @param ctx The RenderContext
     * @return A new Results or the existing Results in the RenderContext or null if no READ permission.
     * @throws SQLException SQLException
     * @throws IOException  IOException
     */
    final public Results getResults(RenderContext ctx) throws SQLException, IOException
    {
        if (!hasPermission(ctx, ReadPermission.class))
            throw new UnauthorizedException();

        DataRegion oldRegion = ctx.getCurrentRegion();
        if (oldRegion != this)
            ctx.setCurrentRegion(this);

        Results results = null;
        boolean success = false;

        try
        {
            TableInfo tinfoMain = getTable();

            results = ctx.getResults();
            if (null == results)
            {
                if (null == tinfoMain)
                {
                    throw new SQLException("Table or query not found: " + getSettings().getQueryName());
                }
                else
                {
                    results = getResults(ctx, isAllowAsync());
                }
            }

            success = true;
            return results;
        }
        finally
        {
            ctx.setCurrentRegion(oldRegion);

            // If getAggregateResults() throws then we won't be returning rs... so close it now
            if (!success)
                ResultSetUtil.close(results);
        }
    }


    protected Results getResults(RenderContext ctx, boolean async) throws SQLException, IOException
    {
        return ctx.getResults(getSelectColumns(), getDisplayColumns(), getTable(), getSettings(), getQueryParameters(), getMaxRows(), getOffset(), getName(), async);
    }


    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        // no extra query columns added by default
    }

    @NotNull
    public Map<String, List<Aggregate.Result>> getAggregateResults(RenderContext ctx, boolean showPaginationCount) throws IOException
    {
        if (_aggregateResults == null)
        {
            Results results = ctx.getResults();
            assert results != null;
            _complete = results.isComplete();

            boolean countAggregate = getMaxRows() > 0 && !_complete && _showPagination && showPaginationCount;
            countAggregate = countAggregate || (getMaxRows() == Table.ALL_ROWS && getTable() != null);

            List<Aggregate> baseAggregates = getSummaryStatsAggregates(ctx.getBaseSummaryStatsProviders());

            if (countAggregate)
            {
                if (baseAggregates.isEmpty() && _complete && results.getSize() >= 0)
                {
                    // Issue 44749. Don't need to do a separate aggregate query as we already know how many rows
                    // came back and that it was the complete results
                    _totalRows = Long.valueOf(results.getSize());
                    _aggregateResults = Collections.emptyMap();
                }
                else
                {
                    List<Aggregate> newAggregates = new LinkedList<>(baseAggregates);

                    newAggregates.add(Aggregate.createCountStar());
                    _aggregateResults = ctx.getAggregates(_displayColumns, getTable(), getSettings(), getName(), newAggregates, getQueryParameters(), isAllowAsync());
                    List<Aggregate.Result> result = _aggregateResults.remove(Aggregate.STAR);

                    //Issue 14863: add null check
                    if (result != null && !result.isEmpty())
                    {
                        Aggregate.Result countStarResult = result.get(0);
                        _totalRows = 0L;
                        if (countStarResult.getValue() instanceof Number)
                            _totalRows = ((Number) countStarResult.getValue()).longValue();
                    }
                }
            }
            else
            {
                _aggregateResults = ctx.getAggregates(_displayColumns, getTable(), getSettings(), getName(), baseAggregates, getQueryParameters(), isAllowAsync());
            }

            // TODO: Move this into RenderContext?
            ActionURL url = ctx.getSortFilterURLHelper();
            PageFlowUtil.saveLastFilter(ctx.getViewContext(), url, getSettings() == null ? "" : getSettings().getLastFilterScope());
        }

        return _aggregateResults;
    }

    @NotNull
    private List<Aggregate> getSummaryStatsAggregates(List<AnalyticsProviderItem> providers)
    {
        List<Aggregate> aggregates = new ArrayList<>();
        for (AnalyticsProviderItem summaryStatsProvider : providers)
            aggregates.addAll(summaryStatsProvider.createAggregates());
        return Collections.unmodifiableList(aggregates);
    }

    //TODO: total number of rows should be pushed down to a property of the TableResultSet
    //We need this temporarily for the QueryView.exportToApiResponse() method
    public Long getTotalRows()
    {
        return _totalRows;
    }

    public void setTotalRows(Long totalRows)
    {
        if (_totalRows == null)
            _totalRows = totalRows;
    }

    public static class ParameterViewBean
    {
        public String dataRegionDomId;
        public String dataRegionName;
        public Collection<QueryService.ParameterDecl> params;
        public Map<String, Object> values;

        ParameterViewBean(String dataRegionDomId, String dataRegionName, Collection<QueryService.ParameterDecl> params, Map<String, Object> values)
        {
            this.dataRegionDomId = dataRegionDomId;
            this.dataRegionName = dataRegionName;
            this.params = params;
            this.values = values;
        }
    }

    @Nullable
    protected SimpleFilter getValidFilter(RenderContext ctx)
    {
        SimpleFilter urlFilter = new SimpleFilter(ctx.getViewContext().getActionURL(), getName());
        for (FieldKey fk : ctx.getIgnoredFilterColumns())
            urlFilter.deleteConditions(fk);
        if (urlFilter.getClauses().isEmpty())
            return null;
        return urlFilter;
    }

    public class ParameterView extends JspView<ParameterViewBean>
    {
        ParameterView(Collection<QueryService.ParameterDecl> params, Map<String, Object> defaults)
        {
            super("/org/labkey/api/data/parameterForm.jsp", new ParameterViewBean(DataRegion.this.getDomId(), DataRegion.this.getName(), params, defaults));
        }
    }

    protected void addHeaderMessage(StringBuilder headerMessage, RenderContext ctx) throws IOException
    {
    }

    private void renderHeader(RenderContext ctx, HtmlWriter out, boolean renderButtons)
    {
        DIV(
            cl("lk-region-bar lk-region-header-bar").
            id(getDomId() + "-headerbar"),
            (DOM.Renderable) ret -> renderButtonBar(ctx, out, renderButtons),
            DIV(cl("pull-right"), DIV(cl("labkey-pagination")))
        ).appendTo(out);

        renderDrawer(out);
        renderViewBar(out);
        renderContextBar(out);
    }

    protected void renderHeaderScript(RenderContext ctx, HtmlWriter writer, Map<String, String> messages, boolean showRecordSelectors)
    {
        JSONObject dataRegionJSON = toJSON(ctx);

        if (messages != null && !messages.isEmpty())
        {
            dataRegionJSON.put("messages", messages);
        }

        HtmlStringBuilder builder = HtmlStringBuilder.of(HttpView.currentPageConfig().getScriptTagStart())
            .unsafeAppend("LABKEY.DataRegion.create(")
            .unsafeAppend(dataRegionJSON.toString(2))
            .unsafeAppend(");\n</script>\n");

        writer.write(builder);
    }

    protected void renderTable(RenderContext ctx, Writer oldWriter) throws SQLException, IOException
    {
        HtmlWriter out = HtmlWriter.of(oldWriter);

        if (!hasPermission(ctx, ReadPermission.class))
        {
            out.write("You do not have permission to read this data");
            return;
        }
        Results results = null;
        try
        {
            boolean showParameterForm = false;
            if (usesResultSet())
            {
                try
                {
                    TableInfo t = getTable();
                    if (null != t && !t.getNamedParameters().isEmpty() && getQueryParameters().isEmpty())
                        showParameterForm = true;
                    else
                        results = getResults(ctx);
                }
                catch (QueryService.NamedParameterNotProvided x)
                {
                    showParameterForm = true;
                }
                catch (SQLException | RuntimeSQLException | IllegalArgumentException | ConversionException x)
                {
                    _errorCreatingResults = true;
                    _showPagination = false;
                    _allowHeaderLock = false;
                    addMessage(new Message(x.getMessage(), MessageType.ERROR, MessagePart.header));
                }
            }

            if (showParameterForm)
            {
                renderParameterForm(ctx, out);
            }
            else
            {
                renderTable(ctx, oldWriter, results);
            }
        }
        finally
        {
            ResultSetUtil.close(results);
        }
    }

    private void renderParameterForm(RenderContext ctx, HtmlWriter out) throws IOException
    {
        _allowHeaderLock = false;

        try
        {
            Collection<QueryService.ParameterDecl> params = getTable().getNamedParameters();
            (new ParameterView(params, null)).render(ctx.getViewContext().getRequest(), ctx.getViewContext().getResponse());
            renderHeaderScript(ctx, out, Collections.emptyMap(), false);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private void renderTable(RenderContext ctx, Writer oldWriter, ResultSet rs) throws IOException
    {
        // renderButtons gets passed down all the things...
        boolean renderButtons = _gridButtonBar.shouldRender(ctx);
        if (renderButtons && _buttonBarConfigs != null && !_buttonBarConfigs.isEmpty())
        {
            if (_gridButtonBar.isLocked())
                _gridButtonBar = new ButtonBar(_gridButtonBar);
            _gridButtonBar.setConfigs(ctx, _buttonBarConfigs);
            addMessage(getMissingCaptionMessage());
        }

        boolean useTableWrap = useTableWrap();
        boolean showRecordSelectors = getShowRecordSelectors(ctx);

        List<DisplayColumn> renderers = getDisplayColumns();

        //determine number of HTML table columns...watch out for hidden display columns
        //and include one extra if showing record selectors
        int colCount = 0;

        for (DisplayColumn col : renderers)
        {
            if (col.isVisible(ctx))
                colCount++;
        }

        if (showRecordSelectors)
            colCount++;

        // TODO: This needs to be migrated to new UI
//        if (!_showPagination && rs instanceof TableResultSet)
//        {
//            TableResultSet tableRS = (TableResultSet) rs;
//            if (!tableRS.isComplete())
//            {
//                out.write("<span class=\"labkey-message\">");
//                out.write(tableRS.getTruncationMessage(getMaxRows()));
//                out.write("</span>");
//            }
//        }

        Map<String, String> messages = prepareMessages(ctx);

        HtmlWriter out = HtmlWriter.of(oldWriter);

        renderFormBegin(ctx, out, ctx.getMode());

        if (useTableWrap)
            oldWriter.write("<table><tbody><tr><td>");
        if (shouldRenderHeader(renderButtons))
        {
            renderHeader(ctx, out, renderButtons);
        }

        renderMessages(out);

        if (useTableWrap)
            oldWriter.write("</td></tr>");
        if (!_errorCreatingResults)
        {
            if (useTableWrap)
                oldWriter.write("<tr><td>");
            renderDataTable(ctx, out, showRecordSelectors, renderers, colCount);
            if (useTableWrap)
                oldWriter.write("</td></tr>");
        }
        if (useTableWrap)
            oldWriter.write("</tbody></table>");

        if (usesResultSet() && rs instanceof TableResultSet && ((TableResultSet) rs).getSize() != -1)
        {
            _rowCount = ((TableResultSet) rs).getSize();
            if (_complete && _totalRows == null)
                _totalRows = getOffset() + _rowCount.intValue();
        }

        renderHeaderScript(ctx, out, messages, showRecordSelectors);
        renderAnalyticsProvidersScripts(ctx, out);

        renderFormEnd(ctx, out);
    }

    private HtmlWriter renderButtonBar(RenderContext ctx, HtmlWriter out, boolean renderButtons)
    {
        if (renderButtons)
        {
            DIV(
                cl("pull-left"),
                (DOM.Renderable) ret -> renderButtons(ctx, out)
            ).appendTo(out);
        }

        return out;
    }

    private void renderDrawer(HtmlWriter out)
    {
        DIV(
            cl("lk-region-bar lk-region-drawer").
            at(id, getDomId() + "-drawer", style, "display:none;")
        ).appendTo(out);
    }

    private void renderBar(HtmlWriter out, List<ContextAction> actions, String idSuffix)
    {
        boolean isEmpty = actions == null || actions.isEmpty();

        DIV(
            cl("lk-region-bar lk-region-context-bar").
            at(
                id, getDomId() + "-" + idSuffix,
                style, isEmpty ? "display:none;" : null
            ),
            (DOM.Renderable) ret -> {
                if (!isEmpty)
                {
                    for (ContextAction ca : actions)
                        ca.render(out);
                }
                return ret;
            }
        ).appendTo(out);
    }

    private void renderContextBar(HtmlWriter out)
    {
        renderBar(out, _contextActions, "ctxbar");
    }

    private void renderViewBar(HtmlWriter out)
    {
        renderBar(out, _viewActions, "viewbar");
    }

    protected void renderMessages(HtmlWriter out)
    {
        // The container <div> is written regardless of _messages being available
        DIV(at(id, getDomId() + "-msgbox"), (DOM.Renderable) ret -> {
            if (_messages != null)
            {
                for (Message message : _messages)
                {
                    boolean isError = MessageType.ERROR.equals(message.getType());
                    boolean isWarning = MessageType.WARNING.equals(message.getType());
                    boolean isThemed = isError || isWarning;

                    // If this is modified, update the client-side renderer in DataRegion.js MsgProto.render()
                    DIV(
                        cl("lk-region-bar" + (isThemed ? " lk-msg-bar" : "")).
                        data("msgpart", message.getArea()),
                        (DOM.Renderable) ren -> {
                            if (isThemed)
                            {
                                DIV(
                                    cl("alert alert-" + (isError ? "danger" : "warning")),
                                    message.getContent()
                                ).appendTo(out);
                            }
                            else
                            {
                                out.write(message.getContent());
                            }

                            return ren;
                        }
                    ).appendTo(out);
                }
            }
            return ret;
        }).appendTo(out);
    }

    private HtmlWriter renderTableContent(RenderContext ctx, HtmlWriter out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount)
    {
        Writer oldWriter = out.unwrap();

        try
        {
            renderGridHeaderColumns(ctx, oldWriter, showRecordSelectors, renderers);

            if (_aggregateRowConfig.getAggregateRowFirst())
                renderAggregatesTableRow(ctx, oldWriter, showRecordSelectors, renderers);

            int rows = renderTableContents(ctx, out, showRecordSelectors, renderers);
            if (rows == 0)
                renderNoRowsMessage(out, colCount);

            if (_aggregateRowConfig.getAggregateRowLast())
                renderAggregatesTableRow(ctx, oldWriter, showRecordSelectors, renderers);
        }
        catch (IOException | SQLException e)
        {
            throw new RuntimeException(e);
        }

        return out;
    }

    private void renderDataTable(RenderContext ctx, HtmlWriter out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount)
    {
        DIV(
            cl("lk-region-ct"),
            DIV(
                cl("lk-region-bar lk-region-section north").id(getDomId() + "-section-n")
            ),
            DIV(
                cl("lk-region-section center").at(style, "display: block;"),
                (DOM.Renderable) ret -> renderCenterContent(ctx, out, showRecordSelectors, renderers, colCount)
            ),
            DIV(cl("lk-region-bar lk-region-section west").id(getDomId() + "-section-w")),
            DIV(cl("lk-region-bar lk-region-section east").id(getDomId() + "-section-e")),
            DIV(cl("lk-region-bar lk-region-section south").id(getDomId() + "-section-s"))
        ).appendTo(out);
    }

    protected HtmlWriter renderCenterContent(RenderContext ctx, HtmlWriter out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount)
    {
        // For now, add lk-region-name AMD data-region-name attributes for test locators. TODO: Migrate to "data-*" only.
        TABLE(
            cl("table-condensed labkey-data-region" + (isShowBorders() ? " table-bordered" : "")).
            data("region-name", getName()).
            lk("region-name", getName()). // TODO: Remove this after all tests check for the "data-" attribute instead of "lk-"
            cl("table-condensed labkey-data-region" + (isShowBorders() ? " table-bordered" : "")).
            id(getDomId()),
            (DOM.Renderable) ret -> renderTableContent(ctx, out, showRecordSelectors, renderers, colCount)
        ).appendTo(out);

        return out;
    }

    private void renderAnalyticsProvidersScripts(RenderContext ctx, HtmlWriter out) throws IOException
    {
        AnalyticsProviderRegistry registry = AnalyticsProviderRegistry.get();
        boolean disableAnalytics = BooleanUtils.toBoolean(ctx.getViewContext().getActionURL().getParameter(ctx.getCurrentRegion().getName() + ".disableAnalytics"));

        if (!disableAnalytics && registry != null && ctx.getBaseAnalyticsProviders() != null && ctx.getFieldMap() != null)
        {
            List<String> scripts = new ArrayList<>();
            for (AnalyticsProviderItem analyticsProviderItem : ctx.getBaseAnalyticsProviders())
            {
                ColumnAnalyticsProvider analyticsProvider = registry.getColumnAnalyticsProvider(analyticsProviderItem.getName());
                ColumnInfo colInfo = ctx.getFieldMap().get(analyticsProviderItem.getFieldKey());

                if (colInfo != null && analyticsProvider != null && !analyticsProvider.requiresPageReload())
                {
                    scripts.add(analyticsProvider.getScript(ctx, getSettings(), colInfo));
                }
            }

            if (!scripts.isEmpty())
            {
                SCRIPT((DOM.Renderable) ret -> {
                    scripts.forEach(script -> out.write(JavaScriptFragment.unsafe(script)));
                    return ret;
                }).appendTo(out);
            }
        }
    }

    @Nullable
    private Message getMissingCaptionMessage()
    {
        Message msg = null;

        if (AppProps.getInstance().isDevMode() && _gridButtonBar.getMissingOriginalCaptions() != null && !_gridButtonBar.getMissingOriginalCaptions().isEmpty())
        {
            StringBuilder content = new StringBuilder();
            content.append("\n").append("WARNING: button bar configuration contains reference to buttons that don't exist.");
            content.append("\n").append("Invalid original text: ");
            StringBuilder captions = new StringBuilder();
            for (String caption : _gridButtonBar.getMissingOriginalCaptions())
            {
                if (!captions.isEmpty())
                    captions.append(", ");
                captions.append(caption);
            }
            captions.append(".");
            content.append(captions);

            msg = new Message(content.toString(), MessageType.WARNING, MessagePart.header);
        }

        return msg;
    }

    protected boolean shouldRenderHeader(boolean renderButtons)
    {
        return ((renderButtons && _buttonBarPosition.atTop() && !_gridButtonBar.getList().isEmpty())
                || (_showPagination && _buttonBarPosition.atTop()));
    }

    protected HtmlWriter renderButtons(RenderContext ctx, HtmlWriter out)
    {
        //adjust position if bbar supplies a position value
        if (_gridButtonBar.getConfiguredPosition() != null)
            setButtonBarPosition(_gridButtonBar.getConfiguredPosition());

        if (_buttonBarPosition.atTop())
        {
            _gridButtonBar.render(ctx, out);
            _buttonBarRendered = true;
        }

        return out;
    }

    /**
     * In almost all cases this is just the standard list of DisplayColumns, but some special cases
     * like the MS2 nested grids may have more columns that get rendered by a nested DataRegion
     */
    protected List<DisplayColumn> getColumnsForMetadata()
    {
        return Collections.unmodifiableList(getDisplayColumns());
    }

    protected JSONObject toJSON(RenderContext ctx)
    {
        JSONObject dataRegionJSON = new JSONObject();
        dataRegionJSON.put("domId", getDomId());
        dataRegionJSON.put("name", getName());

        if (getSettings() != null)
        {
            dataRegionJSON.put("schemaName", getSettings().getSchemaName());
            dataRegionJSON.put("queryName", getSettings().getQueryName());
            dataRegionJSON.put("viewName", getSettings().getViewName());
            dataRegionJSON.put("containerFilter", getSettings().getContainerFilterName());
        }

        dataRegionJSON.put("allowHeaderLock", getAllowHeaderLock());

        User user = ctx.getViewContext().getUser();

        if (ctx.getView() != null)
        {
            dataRegionJSON.put("view", QueryService.get().getCustomViewProperties(ctx.getView(), user));
        }

        // 17021: Faceted Filtering does not respect container path.
        dataRegionJSON.put("containerPath", ctx.getContainerPath());

        //permissions
        JSONObject permissionJSON = new JSONObject();
        TableInfo table = getTable();
        if (table != null)
        {
            permissionJSON.put("insert", table.hasPermission(user, InsertPermission.class));
            permissionJSON.put("update", table.hasPermission(user, UpdatePermission.class));
            permissionJSON.put("delete", table.hasPermission(user, DeletePermission.class));
            permissionJSON.put("admin", table.hasPermission(user, AdminPermission.class));
        }
        dataRegionJSON.put("permissions", permissionJSON);

        dataRegionJSON.put("complete", _complete);
        dataRegionJSON.put("offset", getOffset());
        dataRegionJSON.put("maxRows", getMaxRows());
        dataRegionJSON.put("totalRows", _totalRows);
        dataRegionJSON.put("rowCount", _rowCount);
        dataRegionJSON.put("showPagination", getShowPagination());
        dataRegionJSON.put("showPaginationCount", getShowPaginationCount());
        if (getShowPaginationCount() && AppProps.getInstance().isOptionalFeatureEnabled(EXPERIMENTAL_DATA_REGION_ASYNC_TOTAL_ROWS))
        {
            // Issue 51036: load totalRows count async for DataRegions
            dataRegionJSON.put("showPaginationCount", false);
            dataRegionJSON.put("showPaginationCountAsync", true);
        }
        dataRegionJSON.put("showRows", getShowRows().toString().toLowerCase());
        dataRegionJSON.put("showRecordSelectors", true);
        dataRegionJSON.put("showSelectMessage", _showSelectMessage);
        dataRegionJSON.put("selectionKey", getSelectionKey());
        dataRegionJSON.put("selectorCols", _recordSelectorValueColumns);
        dataRegionJSON.put("selectedCount", ctx.getAllSelected().size());
        dataRegionJSON.put("selectAllURL", getSelectAllURL());
        dataRegionJSON.put("requestURL", ctx.getViewContext().getActionURL().toString());
        dataRegionJSON.put("pkCols", getTable() == null ? null : getTable().getPkColumnNames());
        JSONArray columnsJSON = new JSONArray(JsonWriter.getNativeColProps(getColumnsForMetadata(), null, false).values());
        // Write out a pretty-printed version in dev mode
        dataRegionJSON.put("columns", columnsJSON);

        boolean ignoreFilter = false;
        if (getSettings() != null)
            ignoreFilter = getSettings().getIgnoreUserFilter();
        dataRegionJSON.put("ignoreFilter", ignoreFilter);

        boolean ignoreViewFilter = false;
        if (getSettings() != null)
            ignoreViewFilter = getSettings().getIgnoreViewFilter();
        dataRegionJSON.put("ignoreViewFilter", ignoreViewFilter);

        VisualizationUrls visUrlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        if (visUrlProvider != null)
            dataRegionJSON.put("chartWizardURL", visUrlProvider.getGenericChartDesignerURL(ctx.getContainer(), user, getSettings(), null));

        // TODO: Don't get available container filters from render context.
        // 11082: Populate customize view with list of allowable container filters from the QueryView
        Set<ContainerFilter.Type> allowableContainerFilterTypes = (Set<ContainerFilter.Type>) ctx.get("allowableContainerFilterTypes");
        if (allowableContainerFilterTypes != null && !allowableContainerFilterTypes.isEmpty())
        {
            JSONArray containerFiltersJSON = new JSONArray();
            dataRegionJSON.put("allowableContainerFilters", containerFiltersJSON);
            for (ContainerFilter.Type type : allowableContainerFilterTypes)
            {
                JSONArray containerFilterJSON = new JSONArray();
                containerFiltersJSON.put(containerFilterJSON);
                containerFilterJSON.put(type.name());
                containerFilterJSON.put(type.toString());
            }
        }

        if (_buttonBarRendered)
            dataRegionJSON.put("buttonBarOnRenders", getButtonBarOnRenders());

        return dataRegionJSON;
    }

    private void renderNoRowsMessage(HtmlWriter out, int colCount) throws IOException
    {
        TR(
            TD(
                at(colspan, colCount, style, "white-space:nowrap;"),
                EM(getNoRowsMessage())
            )
        ).appendTo(out);
    }

    protected HtmlString getNoRowsMessage()
    {
        return _noRowsMessage;
    }

    public void setNoRowsMessage(HtmlString noRowsMessage)
    {
        _noRowsMessage = noRowsMessage;
    }

    protected void renderGridHeaderColumns(RenderContext ctx, Writer oldWriter, boolean showRecordSelectors, List<DisplayColumn> renderers)
            throws IOException, SQLException
    {
        oldWriter.write("<thead>");
        oldWriter.write("<tr id=\"" + PageFlowUtil.filter(getDomId() + "-column-header-row") + "\" class=\"labkey-col-header-row\">");

        DisplayColumn detailsColumn = getDetailsUpdateColumn(ctx, renderers, true);
        DisplayColumn updateColumn = getDetailsUpdateColumn(ctx, renderers, false);

        if (showRecordSelectors || (detailsColumn != null || updateColumn != null))
        {
            oldWriter.write(" <th class=\"labkey-column-header labkey-selectors\"");

            int width = 0;
            if (showRecordSelectors)
                width += 45; // account for drop menu
            if (detailsColumn != null)
                width += 15;
            if (updateColumn != null)
                width += 15;
            oldWriter.write(" style=\"width:" + width + "px;\">");

            if (showRecordSelectors)
            {
                final String jsObject = getJavaScriptObjectReference();
                NavTree navtree = new NavTree();

                NavTree selectAll = new NavTree("Select All");
                selectAll.setScript(jsObject + ".selectAll();");
                navtree.addChild(selectAll);

                NavTree selectNone = new NavTree("Select None");
                selectNone.setScript(jsObject + ".selectNone();");
                navtree.addChild(selectNone);

                navtree.addSeparator();

                if (getShowRows() != ShowRows.PAGINATED)
                {
                    NavTree showPaginated = new NavTree("Show Paginated");
                    showPaginated.setScript(jsObject + ".showPaged();");
                    navtree.addChild(showPaginated);
                }

                if (getShowRows() != ShowRows.SELECTED)
                {
                    NavTree showSelected = new NavTree("Show Selected");
                    showSelected.setScript(jsObject + ".showSelected();");
                    navtree.addChild(showSelected);
                }

                if (getShowRows() != ShowRows.UNSELECTED)
                {
                    NavTree showUnselected = new NavTree("Show Unselected");
                    showUnselected.setScript(jsObject + ".showUnselected();");
                    navtree.addChild(showUnselected);
                }

                // NOTE: This is replicated in the Paging Widget (Dataregion.js)
                if (getShowRows() != ShowRows.ALL)
                {
                    NavTree showAll = new NavTree("Show All");
                    showAll.setScript(jsObject + ".showAll();");
                    navtree.addChild(showAll);
                }

                oldWriter.write("<input type=\"checkbox\" title=\"Select/unselect all on current page\" name=\"");
                oldWriter.write(TOGGLE_CHECKBOX_NAME);
                oldWriter.write("\">");

                oldWriter.write("<span class=\"dropdown-toggle\" data-toggle=\"dropdown\"></span>");
                oldWriter.write("<ul class=\"dropdown-menu dropdown-menu-left\">");
                PopupMenuView.renderTree(navtree, oldWriter);
                oldWriter.write("</ul>");
            }

            oldWriter.write("</th>");
        }

        for (DisplayColumn renderer : renderers)
        {
            if (renderer.isVisible(ctx))
            {
                if (renderer instanceof DetailsColumn || renderer instanceof UpdateColumn)
                    continue;

                renderer.renderGridHeaderCell(ctx, oldWriter);
            }
        }

        oldWriter.write("</tr></thead>");
    }

    private void renderAggregatesTableRow(RenderContext ctx, Writer oldWriter, boolean showRecordSelectors, List<DisplayColumn> renderers) throws IOException
    {
        // Issue 51036: load totalRows count async for DataRegions
        boolean asyncTotalRows = AppProps.getInstance().isOptionalFeatureEnabled(EXPERIMENTAL_DATA_REGION_ASYNC_TOTAL_ROWS);
        boolean showPaginationCount = getShowPaginationCount() && !asyncTotalRows;

        Map<String, List<Aggregate.Result>> aggregateResults = getAggregateResults(ctx, showPaginationCount);

        if (!aggregateResults.isEmpty())
        {
            oldWriter.write("<tr class=\"labkey-col-total labkey-row\">");

            DisplayColumn detailsColumn = getDetailsUpdateColumn(ctx, renderers, true);
            DisplayColumn updateColumn = getDetailsUpdateColumn(ctx, renderers, false);

            if (showRecordSelectors || (detailsColumn != null || updateColumn != null))
            {
                oldWriter.write("<td nowrap class=\"labkey-selectors\">&nbsp;</td>");
            }

            for (DisplayColumn renderer : renderers)
            {
                if (renderer.isVisible(ctx))
                {
                    if (renderer instanceof DetailsColumn || renderer instanceof UpdateColumn)
                        continue;

                    oldWriter.write("<td nowrap ");
                    if (renderer.getTextAlign() != null)
                        oldWriter.write(" align=\"" + renderer.getTextAlign() + "\"");
                    oldWriter.write(">");

                    ColumnInfo col = renderer.getColumnInfo();

                    List<Aggregate.Result> result = null;
                    if (col != null)
                    {
                        result = aggregateResults.get(renderer.getColumnInfo().getFieldKey().toString());
                        if (result == null)
                            aggregateResults.get(renderer.getColumnInfo().getAlias());
                    }
                    if (result != null)
                    {
                        for (Aggregate.Result r : result)
                        {
                            String statLabel = r.getAggregate().getDisplayString();
                            Aggregate.Type type = r.getAggregate().getType();

                            oldWriter.write("<div>");
                            oldWriter.write("<span class=\"summary-stat-label\">" + PageFlowUtil.filter(statLabel));
                            if (type.getDescription() != null)
                                PageFlowUtil.popupHelp(HtmlString.of(type.getDescription()), type.getFullLabel()).appendTo(oldWriter);
                            oldWriter.write(":</span>&nbsp;");
                            Pair<String, Boolean> value = r.getFormattedValue(renderer, ctx.getContainer());
                            boolean error = value.second;
                            if (error)
                                oldWriter.write("<span class=\"labkey-error\">");
                            oldWriter.write(PageFlowUtil.filter(value.first));
                            if (error)
                                oldWriter.write("</span>");
                            oldWriter.write("</div>");
                        }
                    }
                    else
                    {
                        oldWriter.write("&nbsp;");
                    }

                    oldWriter.write("</td>");
                }
            }
            oldWriter.write("</tr>");
        }
    }

    protected void renderFormEnd(RenderContext ctx, HtmlWriter out)
    {
        out.write(HtmlString.unsafe("</form>"));
    }

    // Allows subclasses to add table rows at the beginning or end of the table

    /**
     * @return number of rows rendered
     */
    protected int renderTableContents(RenderContext ctx, HtmlWriter out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws SQLException, IOException
    {
        Results results = ctx.getResults();
        int rowIndex = 0;

        // unwrap for efficient use of ResultSetRowMapFactory
        ResultSet rs = results.getResultSet();
        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

        while (rs.next())
        {
            ctx.setRow(factory.getRowMap(rs));
            renderTableRow(ctx, out, showRecordSelectors, renderers, rowIndex++);
        }

        return rowIndex;
    }

    protected String getRowClass(RenderContext ctx, int rowIndex)
    {
        String rowClass = _shadeAlternatingRows && rowIndex % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
        if (isErrorRow(ctx, rowIndex))
            return rowClass + " " + "labkey-error-row";
        return rowClass;
    }

    protected boolean isErrorRow(RenderContext ctx, int rowIndex)
    {
        return false;
    }

    // Allows subclasses to do pre-row and post-row processing
    // CONSIDER: Separate as renderTableRow and renderTableRowContents?
    protected void renderTableRow(RenderContext ctx, HtmlWriter out, boolean showRecordSelectors, List<DisplayColumn> renderers, int rowIndex) throws SQLException, IOException
    {
        DisplayColumn detailsColumn = getDetailsUpdateColumn(ctx, renderers, true);
        DisplayColumn updateColumn = getDetailsUpdateColumn(ctx, renderers, false);

        TR(
            cl(getRowClass(ctx, rowIndex)),
            (DOM.Renderable) ret -> {
                if (showRecordSelectors || (detailsColumn != null || updateColumn != null))
                    renderActionColumn(ctx, out, rowIndex, showRecordSelectors, updateColumn, detailsColumn);

                for (DisplayColumn renderer : renderers)
                {
                    if (renderer.isVisible(ctx))
                    {
                        if (renderer instanceof DetailsColumn || renderer instanceof UpdateColumn)
                            continue;

                        renderer.renderGridDataCell(ctx, out);
                    }
                }

                return ret;
            }
        ).appendTo(out);
    }

    protected DisplayColumn getDetailsUpdateColumn(RenderContext ctx, List<DisplayColumn> renderers, boolean getDetailsCol)
    {
        for (DisplayColumn renderer : renderers)
        {
            if (renderer.isVisible(ctx))
            {
                if ((renderer instanceof DetailsColumn && getDetailsCol)
                        || (renderer instanceof UpdateColumn && !getDetailsCol))
                    return renderer;
            }
        }

        return null;
    }

    protected void renderFormBegin(RenderContext ctx, HtmlWriter out, int mode) throws IOException
    {
        Writer oldWriter = out.unwrap();

        oldWriter.write("<form method=\"post\" id=\"" + PageFlowUtil.filter(getDomId() + "-form") + "\" ");

        String name = getName();
        if (name != null)
        {
            // For now, add lk-region-form AND data-region-from attributes for test locators. TODO: Migrate to "data-*" only.
            oldWriter.write(" lk-region-form=\"" + PageFlowUtil.filter(name) + "\" data-region-form=\"" + PageFlowUtil.filter(name) + "\" ");
        }

        String cls = "form-horizontal";
        if (mode == MODE_DETAILS)
            cls += " form-mode-details";

        oldWriter.write(" class=\"" + cls + "\" ");

        String actionAttr = null == getFormActionUrl() ? "" : getFormActionUrl().getLocalURIString();
        switch (mode)
        {
            case MODE_DETAILS -> oldWriter.write("action=\"begin\">");
            case MODE_INSERT, MODE_UPDATE ->
            {
                if (isFileUploadForm())
                    oldWriter.write("enctype=\"multipart/form-data\" action=\"" + actionAttr + "\">");
                else
                    oldWriter.write("action=\"" + actionAttr + "\">");
            }
            default -> oldWriter.write("action=\"\">");
        }

        renderHiddenFormFields(ctx, out, mode);
    }

    // Output hidden params to be posted
    protected void renderHiddenFormFields(RenderContext ctx, HtmlWriter out, int mode)
    {
        if (mode == MODE_GRID)
            out.write(new InputBuilder().type("hidden").name(DataRegionSelection.DATA_REGION_SELECTION_KEY).value(getSelectionKey()));

        out.write(new CsrfInput(ctx.getViewContext()));

        for (FormField field : _hiddenFormFields)
        {
            out.write(new InputBuilder().type("hidden").name(field.name()).value(field.value()));
        }

        if (mode == MODE_UPDATE_MULTIPLE)
        {
            out.write(new InputBuilder().type("hidden").name(TableViewForm.DATA_SUBMIT_NAME).value("true"));
            out.write(new InputBuilder().type("hidden").name(TableViewForm.DATA_SUBMIT_NAME).value("true"));
        }
    }

    public void setRecordSelectorValueColumns(String... columns)
    {
        _recordSelectorValueColumns = Arrays.asList(columns);
    }

    /**
     * @return an override for the columns to be used for generating record selector checkbox form values. If null, the
     * primary key columns (if any) will be used.
     */
    @Nullable
    public List<String> getRecordSelectorValueColumns()
    {
        return _recordSelectorValueColumns;
    }

    private void renderRecordSelector(RenderContext ctx, HtmlWriter out)
    {
        String checkboxValue = getRecordSelectorValue(ctx);
        boolean enabled = isRecordSelectorEnabled(ctx);
        boolean checked = isRecordSelectorChecked(ctx, checkboxValue);

        new InputBuilder()
            .type("checkbox")
            .title("Select/unselect row")
            .name(getRecordSelectorName(ctx))
            .id(getRecordSelectorId(ctx))
            .value(checkboxValue)
            .checked(checked && enabled)
            .disabled(!enabled).appendTo(out);

        renderExtraRecordSelectorContent(ctx, out);
    }

    protected void renderActionColumn(RenderContext ctx, HtmlWriter out, int rowIndex, boolean showRecordSelectors, @Nullable DisplayColumn updateColumn, @Nullable DisplayColumn detailsColumn)
    {
        if (!showRecordSelectors && updateColumn == null && detailsColumn == null)
            return;

        // TODO: Switch to DOM?

        out.write(HtmlString.unsafe("<td class=\"labkey-selectors\" nowrap>"));

        if (showRecordSelectors)
            renderRecordSelector(ctx, out);
        if (updateColumn != null)
            renderGridCellContents(ctx, out, updateColumn, "fa fa-pencil lk-dr-action-icon");
        if (detailsColumn != null)
            renderGridCellContents(ctx, out, detailsColumn, "fa fa-info-circle lk-dr-action-icon");

        out.write(HtmlString.unsafe("</td>"));
    }

    private void renderGridCellContents(RenderContext ctx, HtmlWriter out, DisplayColumn column, String iconCls)
    {
        Object value = column.getValue(ctx);
        String url = column.renderURL(ctx);

        if (value != null && url != null)
        {
            out.write(PageFlowUtil.iconLink(iconCls, value.toString()).href(url).target(column.getLinkTarget()));
        }
    }

    protected String getRecordSelectorName(RenderContext ctx)
    {
        return SELECT_CHECKBOX_NAME;
    }

    protected String getRecordSelectorValue(RenderContext ctx)
    {
        Map<String, Object> rowMap = ctx.getRow();
        StringBuilder checkboxValue = new StringBuilder();
        String and = "";
        if (_recordSelectorValueColumns == null)
        {
            for (ColumnInfo column : getTable().getPkColumns())
            {
                Object v = column.getValue(ctx);
                // always append the comma, even if there's no value; we need to maintain the correct number
                // of values (even if they're empty) between commas for deterministic parsing (bug 6755)
                checkboxValue.append(and);
                if (null != v)
                    checkboxValue.append(PageFlowUtil.filter(v.toString()));
                and = ",";
            }
        }
        else
        {
            for (String valueColumnName : _recordSelectorValueColumns)
            {
                Object v = (null == rowMap ? null : rowMap.get(valueColumnName));
                // always append the comma, even if there's no value; we need to maintain the correct number
                // of values (even if they're empty) between commas for deterministic parsing (bug 6755)
                checkboxValue.append(and);
                if (null != v)
                    checkboxValue.append(PageFlowUtil.filter(v.toString()));
                and = ",";
            }
        }
        return checkboxValue.toString();
    }

    protected boolean isRecordSelectorChecked(RenderContext ctx, String checkboxValue)
    {
        Set<String> selectedValues = ctx.getAllSelected();
        return selectedValues.contains(checkboxValue);
    }

    protected boolean isRecordSelectorEnabled(RenderContext ctx)
    {
        return true;
    }

    protected void renderExtraRecordSelectorContent(RenderContext ctx, HtmlWriter out)
    {
    }

    protected String getRecordSelectorId(RenderContext ctx)
    {
        return null;
    }

    protected boolean hasPermission(RenderContext ctx, Class<? extends Permission> perm)
    {
        ViewContext viewContext = ctx.getViewContext();
        User user = viewContext.getUser();
        HasPermission p = getTable();
        if (null == p || p instanceof SchemaTableInfo)
            p = viewContext;
        return p.hasPermission(user, perm);
    }

    private void renderDetails(RenderContext ctx, HtmlWriter out) throws SQLException, IOException
    {
        if (!hasPermission(ctx, ReadPermission.class))
        {
            out.write("You do not have permission to read this data");
            return;
        }

        initDetailsResultSet(ctx);
        List<DisplayColumn> renderers = getDisplayColumns();

        renderFormBegin(ctx, out, MODE_DETAILS);

        Writer oldWriter = out.unwrap();
        int rowIndex = 0;

        try (ResultSet rs = ctx.getResults())
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

            oldWriter.write("<table>");

            while (rs.next())
            {
                rowIndex++;
                RowMap<Object> rowMap = factory.getRowMap(rs);
                ctx.setRow(rowMap);

                for (DisplayColumn renderer : renderers)
                {
                    if (!renderer.isVisible(ctx))
                        continue;
                    oldWriter.write("<tr>");
                    renderer.renderDetailsCaptionCell(ctx, oldWriter, null);
                    renderer.renderInputWrapperBegin(oldWriter);
                    renderer.renderDetailsData(ctx, oldWriter);
                    renderer.renderInputWrapperEnd(oldWriter);
                    oldWriter.write("</tr>");
                }
            }

            if (rowIndex == 0)
                renderNoRowsMessage(out, 1);

            oldWriter.write("</table>");

            _detailsButtonBar.render(ctx, out);
        }

        renderFormEnd(ctx, out);
    }


    private void initDetailsResultSet(RenderContext ctx) throws SQLException
    {
        Results results = ctx.getResults();
        if (null != results)
            return;

        if (!hasPermission(ctx, ReadPermission.class))
            throw new UnauthorizedException();

        TableInfo tinfoMain = getTable();

        if (null == tinfoMain)
        {
            _log.info("DataRegion.Details: Could not find table to query from");
            throw new SQLException("No query table in DataRegion.renderTable");
        }
        else
        {
            LinkedHashMap<FieldKey, ColumnInfo> selectKeyMap = getSelectColumns();
            TableSelector selector = new TableSelector(tinfoMain, selectKeyMap.values(), ctx.getBaseFilter(), ctx.getBaseSort()).setForDisplay(true);
            selector.setNamedParameters(getQueryParameters());
            selector.setMaxRows(getMaxRows()).setOffset(getOffset());
            ctx.setResults(selector.getResults());
        }
    }

    private void renderInputForm(RenderContext ctx, HtmlWriter out) throws IOException
    {
        Map<String, Object> rowMap = ctx.getRow();
        //For inserts, just treat the posted strings as the rowmap
        if (null == rowMap)
        {
            TableViewForm form = ctx.getForm();
            if (null != form)
                ctx.setRow((Map) form.getStrings());
        }
        renderForm(ctx, out);
    }

    private void renderUpdateForm(RenderContext ctx, HtmlWriter out) throws IOException
    {
        TableViewForm viewForm = ctx.getForm();
        Map<String, Object> valueMap = ctx.getRow();
        LinkedHashMap<FieldKey, ColumnInfo> selectKeyMap = getSelectColumns();
        ctx.setResults(new ResultsImpl(null, selectKeyMap));
        if (null == valueMap)
        {
            //For updates, the valueMap is the OLD version of the data.
            //If there is no old data, we reselect to get it
            if (null != viewForm.getOldValues())
            {
                //UNDONE: getOldValues() sometimes returns a map and sometimes a bean, this seems broken to me (MAB)
                Object old = viewForm.getOldValues();
                if (old instanceof Map m)
                    valueMap = m;
                else
                    valueMap = new BoundMap(old);
            }
            else
            {
                if (!hasPermission(ctx, ReadPermission.class))
                    throw new UnauthorizedException();

                TableInfo tinfoMain = getTable();
                Collection<Map<String, Object>> maps = new TableSelector(tinfoMain, selectKeyMap.values(), new PkFilter(tinfoMain, viewForm.getPkVals()), null).getMapCollection();
                if (!maps.isEmpty())
                    valueMap = maps.iterator().next();
            }
            ctx.setRow(valueMap);
        }

        renderForm(ctx, out);
    }

    /**
     * This method wraps renderForm and fulfills the values to be exposed in the form to a user during a "bulk edit".
     * In the normal update case the user is shown the current values for a given row, however, when doing a bulk update
     * of multiple rows these values need to be aggregated. Therefore, if all rows share a common value for a field then
     * that value will be passed through, otherwise, the field is resolved as empty and it is left to the UI to convey
     * that there were multiple values available for that field.
     */
    private void renderMultipleUpdateForm(RenderContext ctx, HtmlWriter out) throws IOException
    {
        TableViewForm viewForm = ctx.getForm();
        LinkedHashMap<FieldKey, ColumnInfo> selectKeyMap = getSelectColumns();
        Map<String, Object> rowMap = new HashMap<>();
        QueryService service = QueryService.get();
        QueryLogging queryLogging = new QueryLogging();

        if (!hasPermission(ctx, ReadPermission.class))
            throw new UnauthorizedException();
        TableInfo table = getTable();

        ctx.setResults(new ResultsImpl(null, selectKeyMap));

        String[] selectedRows = viewForm.getSelectedRows();
        if (selectedRows == null)
        {
            throw new NotFoundException("No selected rows found");
        }
        SimpleFilter.InClause clause = new SimpleFilter.InClause(FieldKey.fromParts(viewForm.getPkName()), Arrays.asList(selectedRows), true);
        SimpleFilter pkFilter = new SimpleFilter(clause);

        for (Map.Entry<FieldKey, ColumnInfo> entry : selectKeyMap.entrySet())
        {
            ColumnInfo col = entry.getValue();
            SQLFragment selectSql = service.getSelectSQL(table, Collections.singletonList(col), pkFilter, null, Table.ALL_ROWS, Table.NO_OFFSET, false, queryLogging);

            String safeColumnName = table.getSqlDialect().getColumnSelectName(col.getAlias());
            SQLFragment sql = new SQLFragment("SELECT DISTINCT " + safeColumnName + " AS value FROM (");
            sql.append(selectSql);
            sql.append(") AS D");

            sql = table.getSqlDialect().limitRows(sql, 2);

            SqlSelector selector = new SqlSelector(table.getSchema().getScope(), sql, queryLogging);

            int count = 0;
            Object commonValue = null;
            boolean commonValueSet = false;
            try (ResultSet rs = selector.getResultSet())
            {
                while (rs.next())
                {
                    if (count == 0)
                    {
                        commonValue = rs.getObject(1);
                        commonValueSet = true;
                    }
                    count++;
                }
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }

            if (count == 1 && commonValueSet)
            {
                if (commonValue != null)
                    rowMap.put(entry.getKey().toString(), commonValue);
            }
            else
            {
                rowMap.put(entry.getKey().toString(), null);
            }
        }

        ctx.setRow(rowMap);

        renderForm(ctx, out);
    }

    protected void renderMainErrors(RenderContext ctx, HtmlWriter out)
    {
        HtmlString error = ctx.getErrors("main");
        if (null != error)
            out.write(error);
    }

    private void renderFormField(RenderContext ctx, Writer oldWriter, DisplayColumn renderer) throws IOException
    {
        Set<String> errors = getErrors(ctx, renderer);

        oldWriter.write("<tr class=\"form-group" + (!errors.isEmpty() ? " has-error" : "") + "\">");

        renderer.renderDetailsCaptionCell(ctx, oldWriter, null);

        if (renderer.isEditable())
            renderer.renderInputCell(ctx, oldWriter);
        else
        {
            renderer.renderInputWrapperBegin(oldWriter);
            renderer.renderDetailsData(ctx, oldWriter);
            renderer.renderInputWrapperEnd(oldWriter);
        }

        //TODO: fix bug where first user-defined field is marked as a key and therefore hidden + editable
        oldWriter.write("</tr>");
    }

    private Set<String> getErrors(RenderContext ctx, DisplayColumn... renderers)
    {
        TableViewForm viewForm = ctx.getForm();
        Set<String> errors = new HashSet<>();

        for (DisplayColumn renderer : renderers)
        {
            ColumnInfo col = null;
            if (renderer.isQueryColumn())
                col = renderer.getColumnInfo();

            String error = viewForm == null || col == null ? "" : ctx.getErrors(col).toString();
            if (error != null && !error.isEmpty())
            {
                errors.add(error);
            }
        }

        return errors;
    }

    private void renderForm(RenderContext ctx, HtmlWriter out) throws IOException
    {
        int action = ctx.getMode();

        //if user doesn't have read permissions, don't render anything
        if ((action == MODE_INSERT && !hasPermission(ctx, InsertPermission.class)) ||
           ((action == MODE_UPDATE || action == MODE_UPDATE_MULTIPLE) && !hasPermission(ctx, UpdatePermission.class)))
        {
            out.write("You do not have permission to " +
                    (action == MODE_INSERT ? "Insert" : "Update") +
                    " data in this " + ctx.getContainer().getContainerNoun());
            return;
        }

        Map<String, Object> valueMap = ctx.getRow();

        // Check if we have any value to update
        if (action == MODE_UPDATE && valueMap == null)
        {
            out.write("Could not find data row in " + ctx.getContainer().getContainerNoun());
            return;
        }

        ButtonBar buttonBar;

        if (action == MODE_INSERT)
            buttonBar = _insertButtonBar;
        else
            buttonBar = _updateButtonBar;

        renderFormBegin(ctx, out, action);
        renderMainErrors(ctx, out);

        Writer oldWriter = out.unwrap();

        oldWriter.write("<table>");
        List<DisplayColumn> renderers = getDisplayColumns();

        if (action == MODE_UPDATE_MULTIPLE)
        {
            String msg = "This will edit " + StringUtilsLabKey.pluralize(DataRegionSelection.getSelected(ctx.getViewContext(), null, false).size(), "row");
            oldWriter.write("<tr><td colspan=\"3\">" + msg + "</td></tr>");
        }
        else
        {
            if (renderers.stream().anyMatch(dc -> shouldRender(dc, ctx) && null != dc.getColumnInfo() && !dc.getColumnInfo().isNullable()))
            {
                String msg = "Fields marked with an asterisk * are required.";
                oldWriter.write("<tr><td colspan=\"3\">" + msg + "</td></tr>");
            }
        }

        int span = (_groupTables.isEmpty() || _groupTables.get(0).getGroups().isEmpty()) ?
            1 :
            (_horizontalGroups ?
                _groupTables.get(0).getGroups().get(0).getColumns().size() + 1 :
                _groupTables.get(0).getGroups().size()); // One extra one for the column to reuse the same value

        Set<String> renderedColumns = Sets.newCaseInsensitiveHashSet();

        for (DisplayColumn renderer : renderers)
        {
            if (!shouldRender(renderer, ctx))
                continue;
            renderFormField(ctx, oldWriter, renderer);
            if (null != renderer.getColumnInfo())
                renderedColumns.add(renderer.getColumnInfo().getName());
        }

        //Make sure all pks are included
        if (action == MODE_UPDATE)
        {
            oldWriter.write("<tr><td colspan=\"" + (span + 1) + "\" align=\"left\">");

            // Note: valueMap != null, since we checked this above

            if (valueMap instanceof BoundMap)
                renderOldValues(out, valueMap);
            else
                renderOldValues(out, valueMap, ctx.getFieldMap());

            TableViewForm viewForm = ctx.getForm();
            List<ColumnInfo> pkCols = getTable().getPkColumns();
            for (ColumnInfo pkCol : pkCols)
            {
                String pkColName = pkCol.getName();
                if (!renderedColumns.contains(pkColName))
                {
                    Object pkVal = null;
                    //UNDONE: Should we require a viewForm whenever someone
                    //posts? I tend to think so.
                    if (null != viewForm)
                        pkVal = viewForm.get(pkColName);

                    if (pkVal == null)
                        pkVal = valueMap.get(pkColName);

                    if (null != pkVal)
                    {
                        out.write(
                            new InputBuilder()
                                .type("hidden")
                                .name(viewForm != null ? viewForm.getFormFieldName(pkCol) : pkColName)
                                .value(pkVal.toString())
                        );
                    }
                    renderedColumns.add(pkColName);
                }
            }

            oldWriter.write("</td></tr>");
        }
        oldWriter.write("</table>");

        if (!_groupTables.isEmpty())
        {
            oldWriter.write("<table class=\"labkey-group-tables\">");

            for (GroupTable groupTable : _groupTables)
            {
                List<DisplayColumnGroup> groups = groupTable.getGroups();
                List<String> groupHeadings = groupTable.getGroupHeadings();
                oldWriter.write("<tr><td></td>");
                boolean hasCopyable = false;

                for (DisplayColumnGroup group : groups)
                {
                    if (group.isCopyable() && group.getColumns().size() > 1)
                    {
                        hasCopyable = true;
                        break;
                    }
                }

                if (_horizontalGroups)
                {
                    if (hasCopyable)
                    {
                        writeSameHeader(ctx, oldWriter, groups);
                    }
                    else
                    {
                        oldWriter.write("<td/>");
                    }

                    for (String heading : groupHeadings)
                    {
                        oldWriter.write("<td nowrap><label class=\"control-label\">");
                        oldWriter.write(PageFlowUtil.filter(heading));
                        oldWriter.write("</label></td>");
                    }
                }
                else
                {
                    for (DisplayColumnGroup group : groups)
                        writeColRenderDetailsCaptionCell(ctx, out, group.getColumns().get(0));
                    oldWriter.write("</tr>\n<tr>");
                    if (hasCopyable)
                    {
                        writeSameHeader(ctx, oldWriter, groups);
                        for (DisplayColumnGroup group : groups)
                        {
                            if (group.isCopyable())
                            {
                                group.writeSameCheckboxCell(ctx, oldWriter);
                            }
                            else
                            {
                                oldWriter.write("<td/>");
                            }
                        }
                    }
                    else
                    {
                        oldWriter.write("<td/>");
                    }
                }
                oldWriter.write("</tr>");

                if (_horizontalGroups)
                {
                    for (DisplayColumnGroup group : groups)
                    {
                        oldWriter.write("<tr>");
                        writeColRenderDetailsCaptionCell(ctx, out, group.getColumns().get(0));
                        if (group.isCopyable() && hasCopyable)
                        {
                            group.writeSameCheckboxCell(ctx, oldWriter);
                        }
                        else
                        {
                            oldWriter.write("<td/>");
                        }
                        for (DisplayColumn col : group.getColumns())
                        {
                            if (!shouldRender(col, ctx))
                                continue;
                            col.renderInputCell(ctx, oldWriter);
                        }
                        oldWriter.write("\t</tr>");
                    }
                }
                else
                {
                    for (int i = 0; i < groupHeadings.size(); i++)
                    {
                        oldWriter.write("<tr");
                        String rowClass = getRowClass(ctx, i);
                        if (rowClass != null)
                            oldWriter.write(" class=\"" + rowClass + "\"");
                        oldWriter.write(">");

                        oldWriter.write("<td nowrap><label class=\"control-label\">");
                        oldWriter.write(PageFlowUtil.filter(groupHeadings.get(i)));
                        oldWriter.write("</label></td>");

                        for (DisplayColumnGroup group : groups)
                        {
                            DisplayColumn col = group.getColumns().get(i);
                            if (!shouldRender(col, ctx))
                                continue;
                            col.renderInputCell(ctx, oldWriter);
                        }
                        oldWriter.write("\t</tr>");
                    }
                }

                oldWriter.write("<script type=\"text/javascript\" nonce=\"" + HttpView.currentPageConfig().getScriptNonce() + "\">");
                for (DisplayColumnGroup group : groups)
                    group.writeCopyableJavaScript(ctx, oldWriter);
                oldWriter.write("</script>");
            }

            oldWriter.write("</table>");
        }

        buttonBar.render(ctx, out);
        renderFormEnd(ctx, out);
    }

    private void writeColRenderDetailsCaptionCell(RenderContext ctx, HtmlWriter out, DisplayColumn col) throws IOException
    {
        col.renderDetailsCaptionCell(ctx, out, "control-header-label");
    }

    private void writeSameHeader(RenderContext ctx, Writer oldWriter, List<DisplayColumnGroup> groups) throws IOException
    {
        oldWriter.write("<td nowrap><label class=\"control-label\">");

        PageConfig pageConfig = HttpView.currentPageConfig();
        String id = pageConfig.makeId("selectAll_");
        oldWriter.write("<input id=\"" + id + "\" type=\"checkbox\" name=\"~~SELECTALL~~\" />");
        StringBuilder onChange = new StringBuilder();
        for (DisplayColumnGroup group : groups)
        {
            group.appendCopyableOnChangeHandler(ctx, onChange);
        }
        pageConfig.addHandler(id, "change", onChange.toString());
        oldWriter.write("Same" + PageFlowUtil.popupHelp(HtmlString.of("If selected, all entries on this row will have the same value"), "Same"));

        oldWriter.write("</label></td>");
    }

    protected boolean shouldRender(DisplayColumn renderer, RenderContext ctx)
    {
        return renderer.isVisible(ctx);
    }

    private Boolean _isFileUploadForm = null;

    private boolean isFileUploadForm()
    {
        boolean hasFileFields = false;
        if (null != _isFileUploadForm)
            return _isFileUploadForm.booleanValue();

        for (DisplayColumn dc : _displayColumns)
        {
            ColumnInfo col = dc.getColumnInfo();
            if (null != col && col.getInputType().equalsIgnoreCase("file"))
            {
                hasFileFields = true;
                break;
            }
        }

        _isFileUploadForm = Boolean.valueOf(hasFileFields);

        return hasFileFields;
    }

    private void renderOldValues(HtmlWriter out, Map<String, Object> values)
    {
        Map<String, Object> oldKeys = new HashMap<>();
        String versionColumnName = getTable().getVersionColumnName();
        if (versionColumnName != null)
            oldKeys.put(versionColumnName, values.get(versionColumnName));
        getTable().getPkColumnNames().forEach(name -> oldKeys.put(name, values.get(name)));

        out.write(
            new InputBuilder<>().type("hidden").name(OLD_VALUES_NAME).value(new JSONObject(oldKeys).toString())
        );
    }

    // RowMap keys are the ResultSet alias names, which might be completely mangled.  So, create a new map
    // that's column name -> value and pass it to renderOldValues
    private void renderOldValues(HtmlWriter out, Map<String, Object> valueMap, Map<FieldKey, ColumnInfo> fieldMap) throws IOException
    {
        Map<String, Object> map = new HashMap<>(valueMap.size());

        for (Map.Entry<FieldKey, ColumnInfo> entry : fieldMap.entrySet())
        {
            FieldKey fk = entry.getKey();

            if (1 == fk.size())
            {
                Object value;

                if (valueMap.containsKey(fk.getName()))
                {
                    value = valueMap.get(fk.getName());
                }
                else
                {
                    ColumnInfo info = entry.getValue();
                    value = info.getValue(valueMap);
                }

                map.put(fk.getName(), value);
            }
        }

        renderOldValues(out, map);
    }


    public static List<ColumnInfo> colInfosFromMetaData(ResultSetMetaData md) throws SQLException
    {
        int columnCount = md.getColumnCount();
        List<ColumnInfo> cols = new LinkedList<>();

        for (int i = 1; i <= columnCount; i++)
            cols.add(new BaseColumnInfo(md, i));

        return cols;
    }


    /**
     * Render the data region. All rendering SHOULD go through this function
     * public renderForm, renderTable methods actually all go through here
     * after setting some state
     */
    @Override
    public void render(RenderContext ctx, HtmlWriter out)
    {
        int mode = MODE_GRID;
        if (ctx.getMode() != MODE_NONE)
            mode = ctx.getMode();
        else
            ctx.setMode(mode);

        DataRegion oldRegion = ctx.getCurrentRegion();
        ctx.setCurrentRegion(this);

        prepareDisplayColumns(ctx.getContainer());

        try
        {
            switch (mode)
            {
                case MODE_INSERT -> renderInputForm(ctx, out);
                case MODE_UPDATE -> renderUpdateForm(ctx, out);
                case MODE_UPDATE_MULTIPLE -> renderMultipleUpdateForm(ctx, out);
                case MODE_DETAILS -> renderDetails(ctx, out);
                default -> renderTable(ctx, out.unwrap());
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            ctx.setCurrentRegion(oldRegion);
        }
    }

    // This is the chance for one-time DisplayColumn setup that requires the current context.
    public void prepareDisplayColumns(Container c)
    {
        for (DisplayColumn dc : getDisplayColumns())
        {
            dc.prepare(c);
        }
    }

    private void prepareFilters(RenderContext ctx)
    {
        if (isShowFilterDescription())
        {
            Set<FieldKey> ignoredColumns = ctx.getIgnoredFilterColumns();
            if (!ignoredColumns.isEmpty())
            {
                // TODO: It'd be better to have this be actionable by the user (e.g. show a filter context action
                // with an exclamation point and option to remove or add a link to remove the offending parameter)
                StringBuilder msg;
                if (ignoredColumns.size() == 1)
                {
                    msg = new StringBuilder("Ignoring filter/sort on column '" + ignoredColumns.iterator().next().toDisplayString() + "' because it does not exist.");
                }
                else
                {
                    String sep = "";
                    msg = new StringBuilder("Ignoring filter/sort on columns ");
                    for (FieldKey fieldKey : ignoredColumns)
                    {
                        msg.append(sep);
                        sep = ", ";
                        msg.append("'").append(fieldKey.toDisplayString()).append("'");
                    }
                    msg.append(" because they do not exist.");
                }

                addMessage(new Message(msg.toString(), MessageType.WARNING, "filter"));
            }

            SimpleFilter filter = getValidFilter(ctx);

            if (filter != null && filter.displayFilterText())
            {
                Map<FieldKey, List<SimpleFilter.FilterClause>> fieldClauses = new HashMap<>();
                for (SimpleFilter.FilterClause clause : filter.getClauses())
                {
                    List<FieldKey> fieldKeys = clause.getFieldKeys();

                    // zero/multiple fieldKey clauses NYI
                    if (fieldKeys == null || fieldKeys.size() != 1)
                        continue;

                    // 1. If the filterKey is associated with a current DisplayColumn then generate the
                    //    filter action from that DisplayColumn.
                    // 2. Otherwise fallback to generating the filter action by parsing the filter clause
                    //    and fieldKey.
                    // 3. Be sure to show the same "Column Caption" as what is shown in the table header
                    //    (even if there are duplicates) -- consider calling DataColumn.renderTitle(ctx, out).
                    // 4. If there are multiple (and maybe even if not) then show the FieldKey.toString()
                    //    in the tooltip hover so the user has a chance to disambiguate.

                    FieldKey filterKey = fieldKeys.get(0);

                    if (filterKey != null)
                    {
                        if (!fieldClauses.containsKey(filterKey))
                            fieldClauses.put(filterKey, new ArrayList<>());
                        fieldClauses.get(filterKey).add(clause);
                    }
                }
                for (FieldKey filterKey : fieldClauses.keySet())
                {
                    _contextActions.add(createFilterAction(ctx, fieldClauses.get(filterKey), filterKey));
                }
            }
        }
    }

    /**
     * Add a DataRegion message for invalid conditional formats.
     */
    private void prepareConditionalFormats(RenderContext ctx)
    {
        for (DisplayColumn dc : getDisplayColumns())
        {
            String msg = prepareConditionalFormats(dc);
            if (msg != null)
                addMessage(new Message(msg, MessageType.WARNING, "filter"));
        }
    }

    /**
     * Check for invalid conditional formats.
     */
    private String prepareConditionalFormats(DisplayColumn dc)
    {
        ColumnInfo col = dc.getColumnInfo();
        if (col == null)
            return null;

        for (ConditionalFormat format : col.getConditionalFormats())
        {
            String msg = format.validateFormat(col);
            if (msg != null)
                return msg;
        }

        ColumnInfo displayCol = dc.getDisplayColumnInfo();
        if (displayCol != null && col != displayCol)
        {
            for (ConditionalFormat format : displayCol.getConditionalFormats())
            {
                String msg = format.validateFormat(displayCol);
                if (msg != null)
                    return msg;
            }
        }

        return null;
    }

    /**
     * @param fieldKey The fieldKey to match a DisplayColumn against.
     * @return DisplayColumn associated with the fieldKey if it is shown in this DataRegion, otherwise, null
     */
    @Nullable
    private DisplayColumn getFilterColumn(FieldKey fieldKey)
    {
        return getDisplayColumns().stream().filter(dc -> dc.hasFilterKey(fieldKey)).findFirst().orElse(null);
    }

    @NotNull
    private ContextAction createFilterAction(RenderContext ctx, List<SimpleFilter.FilterClause> clauses, FieldKey filterKey)
    {
        DisplayColumn filterColumn = getFilterColumn(filterKey);

        // Display the filter action using the DisplayColumn information if available
        if (filterColumn != null)
        {
            FieldKey colFilterKey = filterColumn.getFilterKey();

            if (colFilterKey != null)
                return createFilterAction(ctx, clauses, colFilterKey, filterColumn);
        }

        // This is copied from the original addFilterMessage
        String caption = prepareFilterLabel(clauses, new SimpleFilter.ColumnNameFormatter()
        {
            @Override
            public String format(FieldKey fieldKey)
            {
                String formatted = super.format(fieldKey);
                for (String hiddenFilter : HIDDEN_FILTER_COLUMN_SUFFIXES)
                {
                    if (formatted.toLowerCase().endsWith("/" + hiddenFilter.toLowerCase()) ||
                            formatted.toLowerCase().endsWith("." + hiddenFilter.toLowerCase()))
                    {
                        formatted = formatted.substring(0, formatted.length() - (hiddenFilter.length() + 1));
                    }
                }
                int dotIndex = formatted.lastIndexOf('.');
                if (dotIndex >= 0)
                    formatted = formatted.substring(dotIndex + 1);
                return formatted;
            }
        });

        String jsObject = getJavaScriptObjectReference();

        // Still able to remove just cannot edit
        return new ContextAction.Builder()
                .iconCls("filter")
                .onClose(jsObject + ".clearFilter(" + PageFlowUtil.jsString(filterKey.toString()) + "); return false;")
                .text(caption)
                .tooltip("(Unable to edit) " + caption)
                .build();
    }

    @NotNull
    private ContextAction createFilterAction(RenderContext ctx, List<SimpleFilter.FilterClause> clauses, @NotNull FieldKey filterKey, DisplayColumn column)
    {
        // Column is visible in the currently displayed region -- display with same rendered column title
        String caption = prepareFilterLabel(clauses, new SimpleFilter.ColumnNameFormatter()
        {
            @Override
            public String format(FieldKey fieldKey)
            {
                // TODO: Make sure implementors of DisplayColumn override getTitle(ctx)
                return column.getTitle(ctx);
            }
        });

        String tooltip = prepareFilterLabel(clauses, new SimpleFilter.ColumnNameFormatter());

        String jsObject = getJavaScriptObjectReference();

        return new ContextAction.Builder()
            .iconCls("filter")
            .onClick(jsObject + "._openFilter(" + PageFlowUtil.jsString(filterKey.toString()) + ", arguments[0]); return false;")
            .onClose(jsObject + ".clearFilter(" + PageFlowUtil.jsString(filterKey.toString()) + "); return false;")
            .text(caption)
            .tooltip(tooltip)
            .build();
    }

    private String prepareFilterLabel(List<SimpleFilter.FilterClause> clauses, SimpleFilter.ColumnNameFormatter formatter)
    {
        List<String> clauseParts = new ArrayList<>();
        for (SimpleFilter.FilterClause clause : clauses)
        {
            StringBuilder sub = new StringBuilder();
            clause.appendFilterText(sub, formatter);
            clauseParts.add(sub.toString());
        }
        return StringUtils.join(clauseParts, " AND ");
    }

    public Map<String, String> prepareMessages(RenderContext ctx) throws IOException
    {
        StringBuilder headerMsg = new StringBuilder();

        // add any externally supplied messages
        for (MessageSupplier supplier : _messageSuppliers)
        {
            for (Message msg : supplier.getMessages(this))
            {
                if (msg != null)
                    addMessage(msg);
            }
        }

        addHeaderMessage(headerMsg, ctx);
        if (!headerMsg.isEmpty())
            addMessage(new Message(headerMsg.toString(), MessageType.INFO, MessagePart.header));

        //issue 13538: do not try to display filters if error, since this could result in a ConversionException
        if (!_errorCreatingResults)
        {
            prepareParameters(ctx);
            prepareFilters(ctx);
            prepareConditionalFormats(ctx);
        }

        prepareView(ctx);

        Map<String, String> messages = new LinkedHashMap<>();

        if (_messages != null)
        {
            for (Message message : _messages)
                messages.put(message.getArea(), message.getContent());
        }

        return messages;
    }

    protected void prepareParameters(RenderContext ctx)
    {
        // Treat parameters like filters in terms of showing them or not in the header of the grid
        if (isShowFilterDescription())
        {
            Map<String, Object> parameters = getQueryParameters();

            if (!parameters.isEmpty())
            {
                for (Map.Entry<String, Object> entry : parameters.entrySet())
                {
                    String text = entry.getKey() + " = " + entry.getValue();

                    ContextAction.Builder action = new ContextAction.Builder()
                            .iconCls("question")
                            .text(text);

                    _contextActions.add(action.build());
                }
            }
        }
    }

    private void prepareView(RenderContext ctx)
    {
        CustomView view = ctx.getView();

        // 32294: Only display default view context action when it is being edited
        if (view != null && view.getLabel() != null && (!isDefaultView(ctx) || view.isSession()) && getSettings().getAllowChooseView())
        {
            ContextAction.Builder action = new ContextAction.Builder()
                    .iconCls("table")
                    .onClick(getJavaScriptObjectReference() + ".showCustomizeView(); return false;")
                    .text(view.getLabel());
            _viewActions.add(action.build());
        }
    }

    public void setShadeAlternatingRows(boolean shadeAlternatingRows)
    {
        _shadeAlternatingRows = shadeAlternatingRows;
    }

    public boolean isShadeAlternatingRows()
    {
        return _shadeAlternatingRows;
    }

    public void setAggregateRowConfig(AggregateRowConfig config)
    {
        _aggregateRowConfig = config;
    }

    public void setGroupHeadings(List<String> headings)
    {
        if (_groupTables.isEmpty())
            addGroupTable();
        _groupTables.get(_groupTables.size() - 1).setGroupHeadings(headings);
    }

    public boolean getShowPagination()
    {
        return _showPagination;
    }

    public boolean getShowPaginationCount()
    {
        return _showPaginationCount;
    }

    public void setShowPagination(boolean showPagination)
    {
        _showPagination = showPagination;
    }

    public void setShowPaginationCount(boolean showPaginationCount)
    {
        _showPaginationCount = showPaginationCount;
    }

    public enum ButtonBarPosition
    {
        NONE(false, false),
        TOP(true, false),
        BOTTOM(false, true),
        BOTH(true, true);

        ButtonBarPosition(boolean atTop, boolean atBottom)
        {
            _atTop = atTop;
            _atBottom = atBottom;
        }

        final private boolean _atTop;
        final private boolean _atBottom;

        public boolean atTop()
        {
            return _atTop;
        }

        public boolean atBottom()
        {
            return _atBottom;
        }

        public boolean atBoth()
        {
            return _atTop && _atBottom;
        }
    }

    public void setButtonBarPosition(ButtonBarPosition p)
    {
        _buttonBarPosition = p;
    }

    public ButtonBarPosition getButtonBarPosition()
    {
        return _buttonBarPosition;
    }

    public boolean isAllowAsync()
    {
        return allowAsync;
    }

    public void setAllowAsync(boolean allowAsync)
    {
        this.allowAsync = allowAsync;
    }

    public ActionURL getFormActionUrl()
    {
        return _formActionUrl;
    }

    public void setFormActionUrl(ActionURL formActionUrl)
    {
        _formActionUrl = formActionUrl;
    }

    public void addGroupTable()
    {
        _groupTables.add(new GroupTable());
    }

    public void addGroup(DisplayColumnGroup group)
    {
        if (_groupTables.isEmpty())
            addGroupTable();
        List<DisplayColumnGroup> groups = _groupTables.get(_groupTables.size() - 1).getGroups();        // always add to last (current)
        assert groups.isEmpty() || groups.get(0).getColumns().size() == group.getColumns().size() : "Must have matching column counts";
        groups.add(group);
    }

    public boolean isHorizontalGroups()
    {
        return _horizontalGroups;
    }

    public void setHorizontalGroups(boolean horizontalGroups)
    {
        _horizontalGroups = horizontalGroups;
    }

    public String getJavascriptFormReference()
    {
        return "document.forms[" + PageFlowUtil.jsString(getFormId()) + "]";
    }

    public String getJavaScriptObjectReference()
    {
        return getJavaScriptObjectReference(getName());
    }

    public static String getJavaScriptObjectReference(final String regionName)
    {
        return "LABKEY.DataRegions[" + PageFlowUtil.jsString(regionName) + "]";
    }

    public boolean isShowBorders()
    {
        return _showBorders;
    }

    public void setShowBorders(boolean showBorders)
    {
        _showBorders = showBorders;
    }

    public boolean isShowSurroundingBorder()
    {
        return _showSurroundingBorder;
    }

    public void setShowSurroundingBorder(boolean showSurroundingBorder)
    {
        _showSurroundingBorder = showSurroundingBorder;
    }

    @Nullable
    public ActionURL getSelectAllURL()
    {
        return _selectAllURL;
    }

    public void setSelectAllURL(@Nullable ActionURL selectAllURL)
    {
        _selectAllURL = selectAllURL;
    }

    protected boolean usesResultSet()
    {
        return true;
    }

    // Wraps the header and data in a table element to allow either to control the display width.
    protected boolean useTableWrap()
    {
        return true;
    }



    public static class TestCase extends Assert
    {
        // test that we aren't generating extraneous joins to core.container
        @Test
        public void testNoContainerJoin() throws Exception
        {
            var table = DefaultSchema.get(TestContext.get().getUser(), JunitUtil.getTestContainer())
                    .getSchema("wiki")
                    .getTable("CurrentWikiVersions");
            ViewContext context = HttpView.getRootContext();

            {
                var dr = new DataRegion();
                dr.setColumns(List.of(table.getColumn("Title"), table.getColumn("Container")));
                try (Results rs = dr.getResults(new RenderContext(context)))
                {
                    assertEquals(4, rs.getFieldMap().size());
                    assertTrue(rs.getFieldMap().containsKey(FieldKey.fromParts("Title")));
                    assertTrue(rs.getFieldMap().containsKey(FieldKey.fromParts("Container")));
                    assertTrue(rs.getFieldMap().containsKey(FieldKey.fromParts("Container", "DisplayName")));
                    assertTrue(rs.getFieldMap().containsKey(FieldKey.fromParts("RowId")));
                }
            }

            {
                var dr = new DataRegion();
                dr.setColumns(List.of(table.getColumn("Title")));
                try (Results rs = dr.getResults(new RenderContext(context)))
                {
                    assertEquals(2, rs.getFieldMap().size());
                    assertTrue(rs.getFieldMap().containsKey(FieldKey.fromParts("Title")));
                    assertTrue(rs.getFieldMap().containsKey(FieldKey.fromParts("RowId")));
                }
            }
        }
    }
}
