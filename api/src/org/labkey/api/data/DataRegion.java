/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.collections.RowMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.CustomView;
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
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.PopupMenuView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.VisualizationUrls;

import java.io.IOException;
import java.io.StringWriter;
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
import java.util.Set;


public class DataRegion extends DisplayElement
{
    private static final Logger _log = Logger.getLogger(DataRegion.class);

    private String _name = null;
    private QuerySettings _settings = null;
    private boolean _allowHeaderLock = true;
    private final String _domId = "lk-region-" + UniqueID.getServerSessionScopedUID(); // TODO: Consider using UniqueID.getRequestScopedUID(request) instead

    private List<DisplayColumn> _displayColumns = new ArrayList<>();
    private List<AnalyticsProviderItem> _summaryStatsProviders = null;
    private Map<String, List<Aggregate.Result>> _aggregateResults = null;
    private AggregateRowConfig _aggregateRowConfig = new AggregateRowConfig();
    private TableInfo _table = null;
    private ActionURL _selectAllURL = null;
    private boolean _showRecordSelectors = false;
    protected boolean _showSelectMessage = true;
    private boolean _showFilters = true;
    private boolean _sortable = true;
    private boolean _showFilterDescription = true;
    private ButtonBar _gridButtonBar = ButtonBar.BUTTON_BAR_GRID;
    private ButtonBar _insertButtonBar = ButtonBar.BUTTON_BAR_INSERT;
    private ButtonBar _updateButtonBar = ButtonBar.BUTTON_BAR_UPDATE;
    private ButtonBar _detailsButtonBar = ButtonBar.BUTTON_BAR_DETAILS;
    private String _inputPrefix = null;
    private List<String> _recordSelectorValueColumns;
    private int _maxRows = Table.ALL_ROWS;   // Display all rows by default
    private long _offset = 0;
    private List<Pair<String, Object>> _hiddenFormFields = new ArrayList<>();   // Hidden params to be posted (e.g., to pass a query string along with selected grid rows)
    private ButtonBarPosition _buttonBarPosition = ButtonBarPosition.TOP;
    private boolean allowAsync = false;
    private ActionURL _formActionUrl = null;

    private String _noRowsMessage = "No data to show.";

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
    private List<ButtonBarConfig> _buttonBarConfigs = new ArrayList<>();

    public static final int MODE_NONE = 0;
    public static final int MODE_INSERT = 1;
    public static final int MODE_UPDATE = 2;
    public static final int MODE_GRID = 4;
    public static final int MODE_DETAILS = 8;
    public static final int MODE_UPDATE_MULTIPLE = 16;
    public static final int MODE_ALL = MODE_INSERT + MODE_UPDATE + MODE_UPDATE_MULTIPLE + MODE_GRID + MODE_DETAILS;

    public static final String LAST_FILTER_PARAM = ".lastFilter";
    public static final String SELECT_CHECKBOX_NAME = ".select";
    public static final String OLD_VALUES_NAME = ".oldValues";
    public static final String CONTAINER_FILTER_NAME = ".containerFilterName";
    protected static final String TOGGLE_CHECKBOX_NAME = ".toggle";

    public static final String DEFAULTTIME = "Time";
    public static final String DEFAULTDATE = "Date";
    public static final String DEFAULTDATETIME = "DateTime";

    private static final String[] HIDDEN_FILTER_COLUMN_SUFFIXES = {"RowId", "DisplayName", "Description", "Label", "Caption", "Value"};

    private List<ContextAction> _contextActions = new ArrayList<>();
    private List<ContextAction> _viewActions = new ArrayList<>();
    private List<Message> _messages;

    private class GroupTable
    {
        private List<DisplayColumnGroup> _groups = new ArrayList<>();
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
    private List<GroupTable> _groupTables = new ArrayList<>();

    protected class Message
    {
        private String _area;
        private String _content;
        private MessageType _type;

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

    protected void addMessage(Message message)
    {
        if (_messages == null)
            _messages = new ArrayList<>();

        if (null != message)
            _messages.add(message);
    }

    public void adViewErrorMessage(String errorMsg)
    {
        addMessage(new Message("<span class=\"labkey-error\">" + PageFlowUtil.filter(errorMsg) + "</span><br>", MessageType.ERROR, MessagePart.view));
    }

    public void addDisplayColumn(DisplayColumn col)
    {
        assert null != col;
        if (null == col)
            return;
        _displayColumns.add(col);
        if (null != _inputPrefix)
            col.setInputPrefix(_inputPrefix);
    }

    public void addDisplayColumn(int index, DisplayColumn col)
    {
        assert null != col;
        if (null == col)
            return;
        _displayColumns.add(index, col);
        if (null != _inputPrefix)
            col.setInputPrefix(_inputPrefix);
    }

    public void addDisplayColumns(List<DisplayColumn> displayColumns)
    {
        for (DisplayColumn displayColumn : displayColumns)
            addDisplayColumn(displayColumn);
    }

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
        _displayColumns = displayColumns;
        if (null != _inputPrefix)
            for (DisplayColumn dc : _displayColumns)
                dc.setInputPrefix(_inputPrefix);
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

    public void setInputPrefix(String inputPrefix)
    {
        _inputPrefix = inputPrefix;
        for (DisplayColumn dc : _displayColumns)
            dc.setInputPrefix(_inputPrefix);
    }

    public void addButtonBarConfig(ButtonBarConfig buttonBarConfig)
    {
        assert buttonBarConfig != null : "Cannot add a null ButtonBarConfig";
        _buttonBarConfigs.add(buttonBarConfig);
    }

    public void addHiddenFormField(Enum name, String value)
    {
        addHiddenFormField(name.toString(), value);
    }

    public void addHiddenFormField(String name, String value)
    {
        if (null != value)
            _hiddenFormFields.add(Pair.of(name, value));
    }

    public String getHiddenFormFieldValue(String name)
    {
        for (Pair<String, Object> hiddenFormField : _hiddenFormFields)
        {
            if (name.equals(hiddenFormField.getKey()))
            {
                return (String) hiddenFormField.getValue();
            }
        }
        return null;
    }


    @SuppressWarnings({"AssertWithSideEffects"})
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
            case MODE_INSERT:
                return _insertButtonBar;
            case MODE_UPDATE:
                return _updateButtonBar;
            case MODE_UPDATE_MULTIPLE:
                return _updateButtonBar;
            case MODE_GRID:
                return _gridButtonBar;
            case MODE_DETAILS:
                return _detailsButtonBar;
            default:
            {
                _log.error("getting button bar for non existent mode");
                return null;
            }
        }
    }


    public void setButtonBar(ButtonBar buttonBar)
    {
        _insertButtonBar = _updateButtonBar = _gridButtonBar = _detailsButtonBar = buttonBar;
    }

    public void setButtonBar(ButtonBar buttonBar, int mode)
    {
        switch (mode)
        {
            case MODE_INSERT:
                _insertButtonBar = buttonBar;
                return;
            case MODE_UPDATE:
                _updateButtonBar = buttonBar;
                return;
            case MODE_GRID:
                _gridButtonBar = buttonBar;
                return;
            case MODE_DETAILS:
                _detailsButtonBar = buttonBar;
                return;
            default:
                _log.error("Setting button bar for non existent mode");
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
        return getSettings() != null ? getSettings().getOffset() : _offset;
    }

    /**
     * Use {@link QuerySettings#setOffset(long)}.
     */
    @Deprecated
    public void setOffset(long offset)
    {
        if (getSettings() != null)
            getSettings().setOffset(offset);
        else
            _offset = offset;
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
     * Get a ResultSet from the DataRegion.
     * Has the side-effect of setting the ResultSet and this DataRegion
     * on the RenderContext and selecting any aggregates
     * (including the row count aggregate, unless pagination or pagination count are false.)
     * Callers should check for ReadPermission before requesting a ResultSet.
     *
     * @param ctx The RenderContext
     * @return A new ResultSet or the existing ResultSet in the RenderContext or null if no READ permission.
     * @throws SQLException SQLException
     * @throws IOException  IOException
     */
    final public Results getResultSet(RenderContext ctx) throws SQLException, IOException
    {
        if (!ctx.getViewContext().hasPermission("DataRegion.getResultSet()", ReadPermission.class))
            return null;

        DataRegion oldRegion = ctx.getCurrentRegion();
        if (oldRegion != this)
            ctx.setCurrentRegion(this);

        Results rs = null;
        boolean success = false;

        try
        {
            rs = ctx.getResults();
            if (null == rs)
            {
                TableInfo tinfoMain = getTable();
                if (null == tinfoMain)
                {
                    throw new SQLException("Table or query not found: " + getSettings().getQueryName());
                }
                else
                {
                    rs = getResultSet(ctx, isAllowAsync());
                }
            }

            getAggregateResults(ctx);
            success = true;
            return rs;
        }
        finally
        {
            ctx.setCurrentRegion(oldRegion);

            // If getAggregateResults() throws then we won't be returning rs... so close it now
            if (!success)
                ResultSetUtil.close(rs);
        }
    }


    protected Results getResultSet(RenderContext ctx, boolean async) throws SQLException, IOException
    {
        return ctx.getResultSet(getSelectColumns(), getDisplayColumns(), getTable(), getSettings(), getQueryParameters(), getMaxRows(), getOffset(), getName(), async);
    }


    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        // no extra query columns added by default
    }

    public Map<String, List<Aggregate.Result>> getAggregateResults(RenderContext ctx) throws SQLException, IOException
    {
        Results rs = ctx.getResults();
        assert rs != null;
        _complete = rs.isComplete();

        boolean countAggregate = getMaxRows() > 0 && !_complete && _showPagination && _showPaginationCount;
        countAggregate = countAggregate || (getMaxRows() == Table.ALL_ROWS && getTable() != null);

        _summaryStatsProviders = ctx.getBaseSummaryStatsProviders();
        List<Aggregate> baseAggregates = getSummaryStatsAggregates();

        if (countAggregate)
        {
            List<Aggregate> newAggregates = new LinkedList<>();

            if (baseAggregates != null)
                newAggregates.addAll(baseAggregates);

            newAggregates.add(Aggregate.createCountStar());
            _aggregateResults = ctx.getAggregates(_displayColumns, getTable(), getSettings(), getName(), newAggregates, getQueryParameters(), isAllowAsync());
            List<Aggregate.Result> result = _aggregateResults.remove(Aggregate.STAR);

            //Issue 14863: add null check
            if (result != null && result.size() > 0)
            {
                Aggregate.Result countStarResult = result.get(0);
                _totalRows = 0L;
                if (countStarResult.getValue() instanceof Number)
                    _totalRows = ((Number) countStarResult.getValue()).longValue();
            }
        }
        else
        {
            _aggregateResults = ctx.getAggregates(_displayColumns, getTable(), getSettings(), getName(), baseAggregates, getQueryParameters(), isAllowAsync());
        }

        // TODO: Move this into RenderContext?
        ActionURL url = ctx.getSortFilterURLHelper();
        PageFlowUtil.saveLastFilter(ctx.getViewContext(), url, getSettings() == null ? "" : getSettings().getLastFilterScope());

        return _aggregateResults;
    }

    @Nullable
    private List<Aggregate> getSummaryStatsAggregates()
    {
        if (_summaryStatsProviders != null && !_summaryStatsProviders.isEmpty())
        {
            List<Aggregate> aggregates = new ArrayList<>();
            for (AnalyticsProviderItem summaryStatsProvider : _summaryStatsProviders)
                aggregates.addAll(summaryStatsProvider.createAggregates());
            return aggregates;
        }

        return null;
    }

    //TODO: total number of rows should be pushed down to a property of the TableResultSet
    //We need this temporarily for the QueryView.exportToApiResponse() method
    public Long getTotalRows()
    {
        return _totalRows;
    }

    public class ParameterViewBean
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
            super(DataRegion.class, "parameterForm.jsp", new ParameterViewBean(DataRegion.this.getDomId(), DataRegion.this.getName(), params, defaults));
        }
    }

    protected void addHeaderMessage(StringBuilder headerMessage, RenderContext ctx) throws IOException
    {
    }

    private void renderHeader(RenderContext ctx, Writer out, boolean renderButtons) throws IOException
    {
        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-headerbar") + "\" class=\"lk-region-bar lk-region-header-bar\">");
        _renderButtonBarNew(ctx, out, renderButtons);
        _renderPaginationNew(ctx, out);
        out.write("</div>");
        _renderDrawer(ctx, out);
        _renderViewBar(ctx, out);
        _renderContextBar(ctx, out);
    }

    protected void renderHeaderScript(RenderContext ctx, Writer writer, Map<String, String> messages, boolean showRecordSelectors) throws IOException
    {
        JSONObject dataRegionJSON = toJSON(ctx);

        if (messages != null && !messages.isEmpty())
        {
            dataRegionJSON.put("messages", messages);
        }

        StringWriter out = new StringWriter();
        out.write("<script type=\"text/javascript\">\n");
        out.write("LABKEY.DataRegion.create(");
        out.write(dataRegionJSON.toString(2));
        out.write(");\n");
        out.write("</script>\n");
        writer.write(out.toString());
    }

    protected void renderTable(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        if (!ctx.getViewContext().hasPermission(ReadPermission.class))
        {
            out.write("You do not have permission to read this data");
            return;
        }

        ResultSet rs = null;
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
                        rs = getResultSet(ctx);
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
                    addMessage(new Message("<span class=\"labkey-error\">" + PageFlowUtil.filter(x.getMessage()) + "</span><br>", MessageType.ERROR, MessagePart.header));
                }
            }

            if (showParameterForm)
            {
                _renderParameterForm(ctx, out);
            }
            else
            {
                _renderTableNew(ctx, out, rs);
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    private void _renderParameterForm(RenderContext ctx, Writer out) throws IOException
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

    private void _renderTableNew(RenderContext ctx, Writer out, ResultSet rs) throws IOException, SQLException
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

        if (usesResultSet() && rs instanceof TableResultSet && ((TableResultSet) rs).getSize() != -1)
        {
            _rowCount = ((TableResultSet) rs).getSize();
            if (_complete && _totalRows == null)
                _totalRows = getOffset() + _rowCount.intValue();
        }

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

        renderFormBegin(ctx, out, ctx.getMode());

        if (useTableWrap)
            out.write("<table><tbody><tr><td>");
        if (shouldRenderHeader(renderButtons))
        {
            renderHeader(ctx, out, renderButtons);
        }

        renderMessages(ctx, out);

        if (useTableWrap)
            out.write("</td></tr>");
        if (!_errorCreatingResults)
        {
            if (useTableWrap)
                out.write("<tr><td>");
            _renderDataTableNew(ctx, out, showRecordSelectors, renderers, colCount);
            if (useTableWrap)
                out.write("</td></tr>");
        }
        if (useTableWrap)
            out.write("</tbody></table>");

        renderHeaderScript(ctx, out, messages, showRecordSelectors);
        renderAnalyticsProvidersScripts(ctx, out);

        renderFormEnd(ctx, out);
    }

    private void _renderButtonBarNew(RenderContext ctx, Writer out, boolean renderButtons) throws IOException
    {
        if (renderButtons)
        {
            out.write("<div class=\"pull-left\">");
            renderButtons(ctx, out);
            out.write("</div>");
        }
    }

    private void _renderDrawer(RenderContext ctx, Writer out) throws IOException
    {
        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-drawer")+ "\" class=\"lk-region-bar lk-region-drawer\" style=\"display:none;\"></div>");
    }

    private void _renderBar(RenderContext ctx, Writer out, List<ContextAction> actions, String idSuffix) throws IOException
    {
        boolean isEmpty = actions == null || actions.size() == 0;
        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-" + idSuffix) + "\" class=\"lk-region-bar lk-region-context-bar\"");
        if (isEmpty)
            out.write(" style=\"display:none;\">");
        else
        {
            out.write(">");
            for (ContextAction ca : actions)
                out.write(ca.toString());
        }
        out.write("</div>");
    }

    private void _renderContextBar(RenderContext ctx, Writer out) throws IOException
    {
        _renderBar(ctx, out, _contextActions, "ctxbar");
    }

    private void _renderViewBar(RenderContext ctx, Writer out) throws IOException
    {
        _renderBar(ctx, out, _viewActions, "viewbar");
    }

    protected void renderMessages(RenderContext ctx, Writer out) throws IOException
    {
        // The container <div> is written regardless of _messages being available
        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-msgbox") + "\">");
        if (_messages != null)
        {
            for (Message message : _messages)
            {
                boolean isError = MessageType.ERROR.equals(message.getType());
                boolean isWarning = MessageType.WARNING.equals(message.getType());
                boolean isThemed = isError || isWarning;

                // If this is modified, update the client-side renderer in DataRegion.js MsgProto.render()
                out.write("<div class=\"lk-region-bar" + (isThemed ? " lk-msg-bar" : "") + "\" data-msgpart=\"" + PageFlowUtil.filter(message.getArea()) + "\">");

                if (isThemed)
                    out.write("<div class=\"alert alert-" + (isError ? "danger" : "warning") + "\">");

                out.write(message.getContent());

                if (isThemed)
                    out.write("</div>");

                out.write("</div>");
            }
        }
        out.write("</div>");
    }

    private void _renderPaginationNew(RenderContext ctx, Writer out) throws IOException
    {
        out.write("<div class=\"pull-right\">");
        out.write("<div class=\"labkey-pagination\"></div>"); // rendered by client
        out.write("</div>");
    }

    private void renderTableContent(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount) throws IOException, SQLException
    {
        renderGridHeaderColumns(ctx, out, showRecordSelectors, renderers);

        if (_aggregateRowConfig.getAggregateRowFirst())
            renderAggregatesTableRow(ctx, out, showRecordSelectors, renderers);

        int rows = renderTableContents(ctx, out, showRecordSelectors, renderers);
        if (rows == 0)
            renderNoRowsMessage(ctx, out, colCount);

        if (_aggregateRowConfig.getAggregateRowLast())
            renderAggregatesTableRow(ctx, out, showRecordSelectors, renderers);
    }

    private void _renderDataTableNew(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount) throws IOException, SQLException
    {
        out.write("<div class=\"lk-region-ct\">");
        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-section-n") + "\" class=\"lk-region-bar lk-region-section north\"></div>");

        // center section
        out.write("<div class=\"lk-region-section center\" style=\"display: block;\">");
        renderCenterContent(ctx, out, showRecordSelectors, renderers, colCount);
        out.write("</div>");
        // end center section

        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-section-w") + "\" class=\"lk-region-bar lk-region-section west\"></div>");
        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-section-e") + "\" class=\"lk-region-bar lk-region-section east\"></div>");
        out.write("<div id=\"" + PageFlowUtil.filter(getDomId() + "-section-s") + "\" class=\"lk-region-bar lk-region-section south\"></div>");
        out.write("</div>");
    }

    protected void renderCenterContent(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int colCount) throws IOException, SQLException
    {
        // declare table
        out.write("<table id=\"" + PageFlowUtil.filter(getDomId()) + "\"");

        String name = getName();
        String tableCls = "table-condensed labkey-data-region";
        if (name != null)
            out.write(" lk-region-name=\"" + PageFlowUtil.filter(name) + "\"");

        if (isShowBorders())
            tableCls += " table-bordered";

        out.write("class=\"" + tableCls + "\">");

        // table content
        renderTableContent(ctx, out, showRecordSelectors, renderers, colCount);

        out.write("</table>");
        // end declare table
    }

    private void renderAnalyticsProvidersScripts(RenderContext ctx, Writer writer) throws IOException
    {
        AnalyticsProviderRegistry registry = ServiceRegistry.get().getService(AnalyticsProviderRegistry.class);
        boolean disableAnalytics = BooleanUtils.toBoolean(ctx.getViewContext().getActionURL().getParameter(ctx.getCurrentRegion().getName() + ".disableAnalytics"));

        if (!disableAnalytics && registry != null && ctx.getBaseAnalyticsProviders() != null)
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
                StringWriter out = new StringWriter();
                out.write("<script type=\"text/javascript\">\n");
                for (String script : scripts)
                {
                    out.write(script + "\n");
                }
                out.write("</script>\n");
                writer.write(out.toString());
            }
        }
    }

    @Nullable
    private Message getMissingCaptionMessage()
    {
        Message msg = null;

        if (AppProps.getInstance().isDevMode() && _gridButtonBar.getMissingOriginalCaptions() != null && _gridButtonBar.getMissingOriginalCaptions().size() > 0)
        {
            StringBuilder content = new StringBuilder();
            content.append("\n").append("WARNING: button bar configuration contains reference to buttons that don't exist.");
            content.append("\n").append("Invalid original text: ");
            StringBuilder captions = new StringBuilder();
            for (String caption : _gridButtonBar.getMissingOriginalCaptions())
            {
                if (captions.length() > 0)
                    captions.append(", ");
                captions.append(caption);
            }
            captions.append(".");
            content.append(captions.toString());

            msg = new Message(content.toString(), MessageType.WARNING, MessagePart.header);
        }

        return msg;
    }

    protected void renderRegionStart(RenderContext ctx, Writer out, boolean renderButtons, boolean showRecordSelectors, List<DisplayColumn> renderers) throws IOException
    {
        if (renderButtons)
            renderFormBegin(ctx, out, MODE_GRID);
        out.write("\n<div class=\"labkey-data-region-wrap\"><table class=\"labkey-data-region");

        if (isShowBorders())
            out.write(" labkey-show-borders");
        else if (isShowSurroundingBorder())
            out.write(" labkey-show-surrounding-border");

        if (_aggregateResults != null && !_aggregateResults.isEmpty())
            out.write(" labkey-has-col-totals");
        out.write("\"");

        out.write(" id=\"" + PageFlowUtil.filter(getDomId()) + "\"");

        String name = getName();
        if (name != null)
        {
            out.write(" lk-region-name=\"" + PageFlowUtil.filter(name) + "\" ");
        }
        out.write(">\n");

        //colgroup
        out.write("\n<colgroup>");
        if (showRecordSelectors)
            out.write("\n<col class=\"labkey-selectors\" width=\"35\"/>");
        Iterator<DisplayColumn> itr = renderers.iterator();
        DisplayColumn renderer;
        while (itr.hasNext())
        {
            renderer = itr.next();
            if (renderer.isVisible(ctx))
                renderer.renderColTag(out, !itr.hasNext());
        }
        out.write("\n</colgroup>");
    }

    // TODO: Need to migrate or remove usage of renderer.renderGridEnd
    protected void renderRegionEnd(RenderContext ctx, Writer out, boolean renderButtons, List<DisplayColumn> renderers) throws IOException
    {
        out.write("\n</table></div>");
        if (renderButtons)
            renderFormEnd(ctx, out);

        for (DisplayColumn renderer : renderers)
        {
            if (renderer.isVisible(ctx))
                renderer.renderGridEnd(ctx, out);
        }
    }

    protected boolean shouldRenderHeader(boolean renderButtons)
    {
        return ((renderButtons && _buttonBarPosition.atTop() && _gridButtonBar.getList().size() > 0)
                || (_showPagination && _buttonBarPosition.atTop() && !isSmallResultSet()));
    }

    protected void renderButtons(RenderContext ctx, Writer out) throws IOException
    {
        //adjust position if bbar supplies a position value
        if (_gridButtonBar.getConfiguredPosition() != null)
            setButtonBarPosition(_gridButtonBar.getConfiguredPosition());

        if (_buttonBarPosition.atTop())
            _gridButtonBar.render(ctx, out);
    }

    /**
     * In almost all cases this is just the standard list of DisplayColumns, but some special cases
     * like the MS2 nested grids may have more columns that get rendered by a nested DataRegion
     */
    protected List<DisplayColumn> getColumnsForMetadata()
    {
        return getDisplayColumns();
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

        VisualizationUrls visUrlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        if (visUrlProvider != null)
            dataRegionJSON.put("chartWizardURL", visUrlProvider.getGenericChartDesignerURL(ctx.getContainer(), user, getSettings(), null));

        // TODO: Don't get available container filters from render context.
        // 11082: Populate customize view with list of allowable container filters from the QueryView
        Set<ContainerFilter.Type> allowableContainerFilterTypes = (Set<ContainerFilter.Type>) ctx.get("allowableContainerFilterTypes");
        if (allowableContainerFilterTypes != null && allowableContainerFilterTypes.size() > 0)
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
        return dataRegionJSON;
    }

    protected boolean isSmallResultSet()
    {
        if (_totalRows != null && _totalRows < 5)
            return true;
        if (_complete && getOffset() == 0 && _rowCount != null && _rowCount.intValue() < 5)
            return true;
        return false;
    }

    protected void renderNoRowsMessage(RenderContext ctx, Writer out, int colCount) throws IOException
    {
        out.write("<tr><td colspan=\"" + colCount + "\" nowrap=\"true\"><em>");
        out.write(getNoRowsMessage());
        out.write("</em></td></tr>\n");
    }

    protected String getNoRowsMessage()
    {
        return _noRowsMessage;
    }

    public void setNoRowsMessage(String noRowsMessage)
    {
        _noRowsMessage = noRowsMessage;
    }

    protected void renderGridHeaderColumns(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers)
            throws IOException, SQLException
    {
        out.write("<thead>");
        out.write("<tr id=\"" + PageFlowUtil.filter(getDomId() + "-column-header-row") + "\" class=\"labkey-col-header-row\">");

        DisplayColumn detailsColumn = getDetailsUpdateColumn(ctx, renderers, true);
        DisplayColumn updateColumn = getDetailsUpdateColumn(ctx, renderers, false);

        if (showRecordSelectors || (detailsColumn != null || updateColumn != null))
        {
            out.write(" <th class=\"labkey-column-header labkey-selectors\"");

            int width = 0;
            if (showRecordSelectors)
                width += 45; // account for drop menu
            if (detailsColumn != null)
                width += 15;
            if (updateColumn != null)
                width += 15;
            out.write(" style=\"width:" + width + "px;\">");

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

                // NOTE: This is replicated in the Paging Wigdet (Dataregion.js)
                if (getShowRows() != ShowRows.ALL)
                {
                    NavTree showAll = new NavTree("Show All");
                    showAll.setScript(jsObject + ".showAll();");
                    navtree.addChild(showAll);
                }

                out.write("<input type=\"checkbox\" title=\"Select/unselect all on current page\" name=\"");
                out.write(TOGGLE_CHECKBOX_NAME);
                out.write("\">");

                out.write("<span class=\"dropdown-toggle\" data-toggle=\"dropdown\"></span>");
                out.write("<ul class=\"dropdown-menu dropdown-menu-left\">");
                PopupMenuView.renderTree(navtree, out);
                out.write("</ul>");
            }

            out.write("</th>");
        }

        for (DisplayColumn renderer : renderers)
        {
            if (renderer.isVisible(ctx))
            {
                if (renderer instanceof DetailsColumn || renderer instanceof UpdateColumn)
                    continue;

                renderer.renderGridHeaderCell(ctx, out);
            }
        }

        out.write("</tr></thead>");
    }

    protected void renderAggregatesTableRow(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws IOException
    {
        if (_aggregateResults != null && !_aggregateResults.isEmpty())
        {
            out.write("<tr class=\"labkey-col-total labkey-row\">");

            DisplayColumn detailsColumn = getDetailsUpdateColumn(ctx, renderers, true);
            DisplayColumn updateColumn = getDetailsUpdateColumn(ctx, renderers, false);

            if (showRecordSelectors || (detailsColumn != null || updateColumn != null))
            {
                out.write("<td nowrap class=\"labkey-selectors\">&nbsp;</td>");
            }

            for (DisplayColumn renderer : renderers)
            {
                if (renderer.isVisible(ctx))
                {
                    if (renderer instanceof DetailsColumn || renderer instanceof UpdateColumn)
                        continue;

                    out.write("<td nowrap ");
                    if (renderer.getTextAlign() != null)
                        out.write(" align=\"" + renderer.getTextAlign() + "\"");
                    out.write(">");

                    ColumnInfo col = renderer.getColumnInfo();

                    List<Aggregate.Result> result = null;
                    if (col != null)
                    {
                        result = _aggregateResults.get(renderer.getColumnInfo().getFieldKey().toString());
                        if (result == null)
                            _aggregateResults.get(renderer.getColumnInfo().getAlias());
                    }
                    if (result != null)
                    {
                        for (Aggregate.Result r : result)
                        {
                            String statLabel = r.getAggregate().getDisplayString();
                            Aggregate.Type type = r.getAggregate().getType();
                            String statDescr = "";

                            if (type.getDescription() != null)
                                statDescr = PageFlowUtil.helpPopup(type.getFullLabel(), type.getDescription(), true);

                            out.write("<div>");
                            out.write("<span class=\"summary-stat-label\">" + statLabel + statDescr + ":</span>&nbsp;");
                            out.write(PageFlowUtil.filter(r.getFormattedValue(renderer, ctx.getContainer())));
                            out.write("</div>");
                        }
                    }
                    else
                    {
                        out.write("&nbsp;");
                    }

                    out.write("</td>");
                }
            }
            out.write("</tr>");
        }
    }

    protected void renderFormEnd(RenderContext ctx, Writer out) throws IOException
    {
        out.write("</form>");
    }

    // Allows subclasses to add table rows at the beginning or end of the table

    /**
     * @return number of rows rendered
     */
    protected int renderTableContents(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws SQLException, IOException
    {
        Results results = ctx.getResults();
        int rowIndex = 0;
        // unwrap for efficient use of ResultSetRowMapFactory
        try (ResultSet rs = results.getResultSet())
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

            while (rs.next())
            {
                ctx.setRow(factory.getRowMap(rs));
                renderTableRow(ctx, out, showRecordSelectors, renderers, rowIndex++);
            }
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
    protected void renderTableRow(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int rowIndex) throws SQLException, IOException
    {
        out.write("<tr");
        String rowClass = getRowClass(ctx, rowIndex);
        if (rowClass != null)
            out.write(" class=\"" + rowClass + "\"");
        out.write(">");

        DisplayColumn detailsColumn = getDetailsUpdateColumn(ctx, renderers, true);
        DisplayColumn updateColumn = getDetailsUpdateColumn(ctx, renderers, false);

        if (showRecordSelectors || (detailsColumn != null || updateColumn != null))
            renderActionColumn(ctx, out, rowIndex, showRecordSelectors, updateColumn, detailsColumn);

        for (DisplayColumn renderer : renderers)
            if (renderer.isVisible(ctx))
            {
                if (renderer instanceof DetailsColumn || renderer instanceof UpdateColumn)
                        continue;

                renderer.renderGridDataCell(ctx, out);
            }

        out.write("</tr>\n");
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

    protected void renderFormBegin(RenderContext ctx, Writer out, int mode) throws IOException
    {
        out.write("<form method=\"post\" id=\"" + PageFlowUtil.filter(getDomId() + "-form") + "\" ");

        String name = getName();
        if (name != null)
        {
            out.write(" lk-region-form=\"" + PageFlowUtil.filter(name) + "\" ");
        }

        String cls = "form-horizontal";
        if (mode == MODE_DETAILS)
            cls += " form-mode-details";

        out.write(" class=\"" + cls + "\" ");

        String actionAttr = null == getFormActionUrl() ? "" : getFormActionUrl().getLocalURIString();
        switch (mode)
        {
            case MODE_DETAILS:
                out.write("action=\"begin\">");
                break;
            case MODE_INSERT:
            case MODE_UPDATE:
                if (isFileUploadForm())
                    out.write("enctype=\"multipart/form-data\" action=\"" + actionAttr + "\">");
                else
                    out.write("action=\"" + actionAttr + "\">");
                break;
            case MODE_GRID:
                out.write("action=\"\">");
                break;
            default:
                out.write("action=\"\">");
        }

        renderHiddenFormFields(ctx, out, mode);
    }

    // Output hidden params to be posted
    protected void renderHiddenFormFields(RenderContext ctx, Writer out, int mode) throws IOException
    {
        if (mode == MODE_GRID)
            out.write("<input type=\"hidden\" name=\"" + DataRegionSelection.DATA_REGION_SELECTION_KEY + "\" value=\"" + PageFlowUtil.filter(getSelectionKey()) + "\">");
        out.write("<input type=\"hidden\" name=\"" + CSRFUtil.csrfName + "\" value=\"" + CSRFUtil.getExpectedToken(ctx.getViewContext()) + "\">");
        for (Pair<String, Object> field : _hiddenFormFields)
        {
            out.write("<input type=\"hidden\" name=\"" + PageFlowUtil.filter(field.first) + "\" value=\"" + PageFlowUtil.filter((String) field.second) + "\">");
        }

        if (mode == MODE_UPDATE_MULTIPLE)
        {
            out.write("<input type=\"hidden\" name=\"" + TableViewForm.DATA_SUBMIT_NAME + "\" value=\"true\">");
            out.write("<input type=\"hidden\" name=\"" + TableViewForm.BULK_UPDATE_NAME + "\" value=\"true\">");
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

    protected void renderRecordSelector(RenderContext ctx, Writer out) throws IOException
    {
        out.write("<input type=\"checkbox\" title=\"Select/unselect row\" name=\"");
        out.write(getRecordSelectorName(ctx));
        out.write("\" ");
        String id = getRecordSelectorId(ctx);
        if (id != null)
        {
            out.write("id=\"");
            out.write(id);
            out.write("\" ");
        }
        out.write("value=\"");
        String checkboxValue = getRecordSelectorValue(ctx);
        out.write(checkboxValue);
        out.write("\"");
        boolean enabled = isRecordSelectorEnabled(ctx);
        boolean checked = isRecordSelectorChecked(ctx, checkboxValue);
        if (checked && enabled)
        {
            out.write(" checked");
        }

        if (!enabled)
            out.write(" DISABLED");
        out.write(">");
        renderExtraRecordSelectorContent(ctx, out);
    }

    protected void renderActionColumn(RenderContext ctx, Writer out, int rowIndex, boolean showRecordSelectors, @Nullable DisplayColumn updateColumn, @Nullable DisplayColumn detailsColumn) throws IOException
    {
        if (!showRecordSelectors && updateColumn == null && detailsColumn == null)
            return;

        out.write("<td class=\"labkey-selectors\" nowrap>");

        if (showRecordSelectors)
            renderRecordSelector(ctx, out);
        if (updateColumn != null)
            renderGridCellContents(ctx, out, updateColumn, "fa fa-pencil lk-dr-action-icon");
        if (detailsColumn != null)
            renderGridCellContents(ctx, out, detailsColumn, "fa fa-info-circle lk-dr-action-icon");

        out.write("</td>");
    }

    public void renderGridCellContents(RenderContext ctx, Writer out, DisplayColumn column, String iconCls) throws IOException
    {
        Object value = column.getValue(ctx);
        String url = column.renderURL(ctx);

        if (value != null && url != null)
        {
            Map<String, String> props;
            if (column.getLinkTarget() != null)
            {
                props = Collections.singletonMap("target", column.getLinkTarget());
            }
            else
            {
                props = Collections.emptyMap();
            }

            out.write(PageFlowUtil.iconLink(iconCls, value.toString(), url, null, null, props));
        }
    }

    protected String getRecordSelectorName(RenderContext ctx)
    {
        return SELECT_CHECKBOX_NAME;
    }

    protected String getRecordSelectorValue(RenderContext ctx)
    {
        Map rowMap = ctx.getRow();
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

    protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
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
        // TODO : tables need to accurately represent their own permissions
        // TODO : or maybe we need DataRegion.setPermissionToCheck(HasPermissions)
        // TODO : and perhaps consolidate with permission check in DisplayElement.shouldRender() ?
        if (null == p || p instanceof SchemaTableInfo)
            p = viewContext;

        return p.hasPermission(user, perm);
    }

    private void renderDetails(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        if (!hasPermission(ctx, ReadPermission.class))
        {
            out.write("You do not have permission to read this data");
            return;
        }

        initDetailsResultSet(ctx);
        List<DisplayColumn> renderers = getDisplayColumns();

        renderFormBegin(ctx, out, MODE_DETAILS);

        RowMap rowMap = null;
        int rowIndex = 0;

        try (ResultSet rs = ctx.getResults())
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

            out.write("<table>");

            while (rs.next())
            {
                rowIndex++;
                rowMap = factory.getRowMap(rs);
                ctx.setRow(rowMap);

                for (DisplayColumn renderer : renderers)
                {
                    if (!renderer.isVisible(ctx) || (renderer.getDisplayModes() & MODE_DETAILS) == 0)
                        continue;
                    out.write("<tr>");
                    renderer.renderDetailsCaptionCell(ctx, out, null);
                    renderer.renderInputWrapperBegin(out);
                    renderer.renderDetailsData(ctx, out);
                    renderer.renderInputWrapperEnd(out);
                    out.write("</tr>");
                }
            }

            if (rowIndex == 0)
                renderNoRowsMessage(ctx, out, 1);

            out.write("</table>");

            renderDetailsHiddenFields(out, rowMap);
            _detailsButtonBar.render(ctx, out);
        }

        renderFormEnd(ctx, out);
    }


    private void initDetailsResultSet(RenderContext ctx) throws SQLException
    {
        Results rs = ctx.getResults();
        if (null != rs)
            return;

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

    private void renderDetailsHiddenFields(Writer out, Map rowMap) throws IOException
    {
        if (null != rowMap)
        {
            List<ColumnInfo> pkCols = getTable().getPkColumns();

            for (ColumnInfo pkCol : pkCols)
            {
                assert null != rowMap.get(pkCol.getAlias());
                out.write("<input type=\"hidden\" name=\"");
                out.write(pkCol.getName());
                out.write("\" value=\"");
                out.write(PageFlowUtil.filter(rowMap.get(pkCol.getAlias()).toString()));
                out.write("\">");
            }
        }
    }

    private void renderInputForm(RenderContext ctx, Writer out) throws IOException
    {
        Map rowMap = ctx.getRow();
        //For inserts, just treat the posted strings as the rowmap
        if (null == rowMap)
        {
            TableViewForm form = ctx.getForm();
            if (null != form)
                ctx.setRow((Map) form.getStrings());
        }
        renderForm(ctx, out);
    }

    private void renderUpdateForm(RenderContext ctx, Writer out) throws IOException
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
                if (old instanceof Map)
                    valueMap = (Map) old;
                else
                    valueMap = new BoundMap(old);
            }
            else
            {
                Map<String, Object>[] maps = new TableSelector(getTable(), selectKeyMap.values(), new PkFilter(getTable(), viewForm.getPkVals()), null).getMapArray();
                if (maps.length > 0)
                    valueMap = maps[0];
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
     * @param ctx
     * @param out
     * @throws IOException
     */
    private void renderMultipleUpdateForm(RenderContext ctx, Writer out) throws IOException
    {
        TableViewForm viewForm = ctx.getForm();
        LinkedHashMap<FieldKey, ColumnInfo> selectKeyMap = getSelectColumns();
        Map<String, Object> rowMap = new HashMap<>();
        QueryService service = QueryService.get();
        QueryLogging queryLogging = new QueryLogging();
        TableInfo table = getTable();
        SqlSelector selector;

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

            selector = new SqlSelector(table.getSchema().getScope(), sql, queryLogging);

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

    protected void renderMainErrors(RenderContext ctx, Writer out) throws IOException
    {
        String error = ctx.getErrors("main");
        if (null != error)
            out.write(error);
    }

    protected void renderFormField(RenderContext ctx, Writer out, DisplayColumn renderer) throws IOException
    {
        Set<String> errors = getErrors(ctx, renderer);

        out.write("<tr class=\"form-group" + (errors.size() > 0 ? " has-error" : "") + "\">");

        renderer.renderDetailsCaptionCell(ctx, out, null);

        if (renderer.isEditable())
            renderer.renderInputCell(ctx, out);
        else
        {
            renderer.renderInputWrapperBegin(out);
            renderer.renderDetailsData(ctx, out);
            renderer.renderInputWrapperEnd(out);
        }

        //TODO: fix bug where first user-defined field is marked as a key and therefore hidden + editable
        out.write("</tr>");
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

            String error = viewForm == null || col == null ? "" : ctx.getErrors(col);
            if (error != null && error.length() > 0)
            {
                errors.add(error);
            }
        }

        return errors;
    }

    private void renderForm(RenderContext ctx, Writer out) throws IOException
    {
        int action = ctx.getMode();
        Map valueMap = ctx.getRow();

        //if user doesn't have read permissions, don't render anything
        if ((action == MODE_INSERT && !hasPermission(ctx, InsertPermission.class)) ||
           ((action == MODE_UPDATE || action == MODE_UPDATE_MULTIPLE) && !hasPermission(ctx, UpdatePermission.class)))
        {
            out.write("You do not have permission to " +
                    (action == MODE_INSERT ? "Insert" : "Update") +
                    " data in this " + ctx.getContainer().getContainerNoun());
            return;
        }

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

        out.write("<table>");

        if (action == MODE_UPDATE_MULTIPLE)
        {
            String msg = "This will edit " + ctx.getForm().getSelectedRows().length + " rows.";

            out.write("<tr><td colspan=\"3\">" + msg + "</td></tr>");
        }

        List<DisplayColumn> renderers = getDisplayColumns();

        for (DisplayColumn renderer : renderers)
        {
            if (shouldRender(renderer, ctx) && null != renderer.getColumnInfo() && !renderer.getColumnInfo().isNullable())
            {
                String msg = "Fields marked with an asterisk * are required.";

                out.write("<tr><td colspan=\"3\">" + msg + "</td></tr>");
                break;
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
            renderFormField(ctx, out, renderer);
            if (null != renderer.getColumnInfo())
                renderedColumns.add(renderer.getColumnInfo().getName());
        }

        if (!_groupTables.isEmpty())
        {
            out.write("<table class=\"labkey-group-tables\">");

            for (GroupTable groupTable : _groupTables)
            {
                List<DisplayColumnGroup> groups = groupTable.getGroups();
                List<String> groupHeadings = groupTable.getGroupHeadings();
                out.write("<tr><td></td>");
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
                        writeSameHeader(ctx, out, groups);
                    }
                    else
                    {
                        out.write("<td/>");
                    }

                    for (String heading : groupHeadings)
                    {
                        out.write("<td nowrap><label class=\"control-label\">");
                        out.write(PageFlowUtil.filter(heading));
                        out.write("</label></td>");
                    }
                }
                else
                {
                    for (DisplayColumnGroup group : groups)
                        writeColRenderDetailsCaptionCell(ctx, out, group.getColumns().get(0));
                    out.write("</tr>\n<tr>");
                    if (hasCopyable)
                    {
                        writeSameHeader(ctx, out, groups);
                        for (DisplayColumnGroup group : groups)
                        {
                            if (group.isCopyable())
                            {
                                group.writeSameCheckboxCell(ctx, out);
                            }
                            else
                            {
                                out.write("<td/>");
                            }
                        }
                    }
                    else
                    {
                        out.write("<td/>");
                    }
                }
                out.write("</tr>");

                if (_horizontalGroups)
                {
                    for (DisplayColumnGroup group : groups)
                    {
                        out.write("<tr>");
                        writeColRenderDetailsCaptionCell(ctx, out, group.getColumns().get(0));
                        if (group.isCopyable() && hasCopyable)
                        {
                            group.writeSameCheckboxCell(ctx, out);
                        }
                        else
                        {
                            out.write("<td/>");
                        }
                        for (DisplayColumn col : group.getColumns())
                        {
                            if (!shouldRender(col, ctx))
                                continue;
                            col.renderInputCell(ctx, out);
                        }
                        out.write("\t</tr>");
                    }
                }
                else
                {
                    for (int i = 0; i < groupHeadings.size(); i++)
                    {
                        out.write("<tr");
                        String rowClass = getRowClass(ctx, i);
                        if (rowClass != null)
                            out.write(" class=\"" + rowClass + "\"");
                        out.write(">");

                        out.write("<td nowrap><label class=\"control-label\">");
                        out.write(PageFlowUtil.filter(groupHeadings.get(i)));
                        out.write("</label></td>");

                        for (DisplayColumnGroup group : groups)
                        {
                            DisplayColumn col = group.getColumns().get(i);
                            if (!shouldRender(col, ctx))
                                continue;
                            col.renderInputCell(ctx, out);
                        }
                        out.write("\t</tr>");
                    }
                }

                out.write("<script type=\"text/javascript\">");
                for (DisplayColumnGroup group : groups)
                    group.writeCopyableJavaScript(ctx, out);
                out.write("</script>");
            }

            out.write("</table>");
        }

        out.write("<tr><td colspan=\"" + (span + 1) + "\" align=\"left\">");

        //Make sure all pks are included
        if (action == MODE_UPDATE)
        {
            if (valueMap != null)
            {
                if (valueMap instanceof BoundMap)
                    renderOldValues(out, ((BoundMap) valueMap).getBean());
                else
                    renderOldValues(out, valueMap, ctx.getFieldMap());
            }

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

                    if (pkVal == null && valueMap != null)
                        pkVal = valueMap.get(pkColName);

                    if (null != pkVal)
                    {
                        out.write("<input type='hidden' name='");
                        if (viewForm != null)
                            out.write(viewForm.getFormFieldName(pkCol));
                        else
                            out.write(pkColName);
                        out.write("' value=\"");
                        out.write(PageFlowUtil.filter(pkVal.toString()));
                        out.write("\">");
                    }
                    renderedColumns.add(pkColName);
                }
            }
        }

        out.write("</td></tr></table>");
        buttonBar.render(ctx, out);
        renderFormEnd(ctx, out);
    }

    private void writeColRenderDetailsCaptionCell(RenderContext ctx, Writer out, DisplayColumn col) throws IOException
    {
        col.renderDetailsCaptionCell(ctx, out, "control-header-label");
    }

    private void writeSameHeader(RenderContext ctx, Writer out, List<DisplayColumnGroup> groups) throws IOException
    {
        out.write("<td nowrap><label class=\"control-label\">");

        out.write("<input type=\"checkbox\" name=\"~~SELECTALL~~\" onchange=\"");
        for (DisplayColumnGroup group : groups)
        {
            group.writeCopyableOnChangeHandler(ctx, out);
        }
        out.write("\" />");
        out.write("Same" + PageFlowUtil.helpPopup("Same", "If selected, all entries on this row will have the same value"));

        out.write("</label></td>");
    }

    protected boolean shouldRender(DisplayColumn renderer, RenderContext ctx)
    {
        return (renderer.isVisible(ctx) && (renderer.getDisplayModes() & (MODE_UPDATE | MODE_UPDATE_MULTIPLE | MODE_INSERT)) != 0);
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


    private void renderOldValues(Writer out, Object values) throws IOException
    {
        out.write("<input name=\"" + OLD_VALUES_NAME + "\" type=\"hidden\" value=\"");
        out.write(PageFlowUtil.encodeObject(values));
        out.write("\">");
    }


    // RowMap keys are the ResultSet alias names, which might be completely mangled.  So, create a new map
    // that's column name -> value and pass it to renderOldValues
    private void renderOldValues(Writer out, Map<String, Object> valueMap, Map<FieldKey, ColumnInfo> fieldMap) throws IOException
    {
        Map<String, Object> map = new HashMap<>(valueMap.size());

        for (Map.Entry<FieldKey, ColumnInfo> entry : fieldMap.entrySet())
        {
            FieldKey fk = entry.getKey();

            if (1 == fk.getParts().size())
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
            cols.add(new ColumnInfo(md, i));

        return cols;
    }


    /**
     * Render the data region. All rendering SHOULD go through this function
     * public renderForm, renderTable methods actually all go through here
     * after setting some state
     */
    public void render(RenderContext ctx, Writer out) throws IOException
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
                case MODE_INSERT:
                    renderInputForm(ctx, out);
                    return;
                case MODE_UPDATE:
                    renderUpdateForm(ctx, out);
                    return;
                case MODE_UPDATE_MULTIPLE:
                    renderMultipleUpdateForm(ctx, out);
                    return;
                case MODE_DETAILS:
                    renderDetails(ctx, out);
                    return;
                default:
                    renderTable(ctx, out);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
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
                        _contextActions.add(createFilterAction(ctx, clause, filterKey));
                    }
                }
            }
        }
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
    private ContextAction createFilterAction(RenderContext ctx, SimpleFilter.FilterClause clause, FieldKey filterKey)
    {
        DisplayColumn filterColumn = getFilterColumn(filterKey);

        // Display the filter action using the DisplayColumn information if available
        if (filterColumn != null)
        {
            FieldKey colFilterKey = filterColumn.getFilterKey();

            if (colFilterKey != null)
                return createFilterAction(ctx, clause, colFilterKey, filterColumn);
        }

        // This is copied from the original addFilterMessage
        StringBuilder caption = new StringBuilder();
        clause.appendFilterText(caption, new SimpleFilter.ColumnNameFormatter()
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
                int slashIndex = formatted.lastIndexOf('/');
                if (slashIndex >= 0)
                    formatted = formatted.substring(slashIndex);
                return formatted;
            }
        });

        String jsObject = getJavaScriptObjectReference();

        // Still able to remove just cannot edit
        return new ContextAction.Builder()
                .iconCls("filter")
                .onClose(jsObject + ".clearFilter(" + PageFlowUtil.jsString(filterKey.toString()) + "); return false;")
                .text(caption.toString())
                .tooltip("(Unable to edit) " + caption.toString())
                .build();
    }

    @NotNull
    private ContextAction createFilterAction(RenderContext ctx, SimpleFilter.FilterClause clause, @NotNull FieldKey filterKey, DisplayColumn column)
    {
        // Column is visible in the currently displayed region -- display with same rendered column title
        StringBuilder caption = new StringBuilder();
        clause.appendFilterText(caption, new SimpleFilter.ColumnNameFormatter()
        {
            @Override
            public String format(FieldKey fieldKey)
            {
                // TODO: Make sure implementors of DisplayColumn override getTitle(ctx)
                return column.getTitle(ctx);
            }
        });

        StringBuilder tooltip = new StringBuilder();
        clause.appendFilterText(tooltip, new SimpleFilter.ColumnNameFormatter());

        String jsObject = getJavaScriptObjectReference();

        return new ContextAction.Builder()
                .iconCls("filter")
                .onClick(jsObject + "._openFilter(" + PageFlowUtil.jsString(filterKey.toString()) + ", arguments[0]); return false;")
                .onClose(jsObject + ".clearFilter(" + PageFlowUtil.jsString(filterKey.toString()) + "); return false;")
                .text(caption.toString())
                .tooltip(tooltip.toString())
                .build();
    }

    protected Map<String, String> prepareMessages(RenderContext ctx) throws IOException
    {
        StringBuilder headerMsg = new StringBuilder();

        addHeaderMessage(headerMsg, ctx);
        if (headerMsg.length() > 0)
            addMessage(new Message(headerMsg.toString(), MessageType.INFO, MessagePart.header));

        //issue 13538: do not try to display filters if error, since this could result in a ConversionException
        if (!_errorCreatingResults)
        {
            prepareParameters(ctx);
            prepareFilters(ctx);
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

    private void prepareParameters(RenderContext ctx)
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

    private void prepareView(RenderContext ctx)
    {
        CustomView view = ctx.getView();

        // 32294: Only display default view context action when it is being edited
        if (view != null && view.getLabel() != null && (!isDefaultView(ctx) || view.isSession()))
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
}
