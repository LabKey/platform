/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.collections.LongArrayList;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.Formats;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages row selection states, scoped to schema/query and possibly a separate selection key.
 * Uses a synchronized Set. As per documentation on {@link Collections#synchronizedSet(Set)}, callers
 * should do their own synchronization on the set itself if they are operating on it one element at a time
 * and want to have a consistent view. This allows for the backing set to be a {@link LinkedHashSet}.
 */
public class DataRegionSelection
{
    public static final String SELECTED_VALUES = ".selectValues";
    public static final String SEPARATOR = "$";
    public static final String DATA_REGION_SELECTION_KEY = "dataRegionSelectionKey";

    // Issue 53997: Establish a maximum size for query selections
    public static final int MAX_QUERY_SELECTION_SIZE = 100_000;

    // set/updated using query-setSnapshotSelection
    // can be used to hold an arbitrary set of selections in session
    // example usage: set a filtered set of selected values in session
    public static final String SNAPSHOT_SELECTED_VALUES = ".snapshotSelectValues";

    private static @NotNull String getSessionAttributeKey(@NotNull String path, @NotNull String key, boolean useSnapshot)
    {
        return path + key + (useSnapshot ? SNAPSHOT_SELECTED_VALUES : SELECTED_VALUES);
    }

    private static @NotNull Set<String> getSet(ViewContext context, @Nullable String key, boolean create)
    {
        return getSet(context, key, create, false);
    }

    /**
     *  * Uses a synchronized Set. As per documentation on {@link Collections#synchronizedSet(Set)}, callers
     *  * should do their own synchronization on the set itself if they are operating on it one element at a time
     *  * and want to have a consistent view
     */
    private static @NotNull Set<String> getSet(ViewContext context, @Nullable String key, boolean create, boolean useSnapshot)
    {
        if (key == null)
            key = getSelectionKeyFromRequest(context);

        if (key != null)
        {
            key = getSessionAttributeKey(context.getContainer().getPath(), key, useSnapshot);
            var request = context.getRequest();
            HttpSession session = request != null ? context.getRequest().getSession(false) : null;
            if (session != null)
            {
                // Ensure that two different requests don't end up creating two different selection sets
                // in the same session
                synchronized (SessionHelper.getSessionLock(session))
                {
                    @SuppressWarnings("unchecked") Set<String> result = (Set<String>) session.getAttribute(key);
                    if (result == null)
                    {
                        result = Collections.synchronizedSet(new LinkedHashSet<>());

                        if (create)
                            session.setAttribute(key, result);
                    }
                    return result;
                }
            }
        }

        return Collections.synchronizedSet(new LinkedHashSet<>());
    }

    /**
     * Composes a selection key string used to uniquely identify the selected items
     * of a given dataregion. Nulls are allowed.
     */
    public static String getSelectionKey(String schemaName, String queryName, String viewName, String dataRegionName)
    {
        StringBuilder buf = new StringBuilder();

        for (String s : new String[]{schemaName, queryName, viewName, dataRegionName})
        {
            buf.append(SEPARATOR);
            if (s != null)
                buf.append(s);
        }

        return buf.toString();
    }

    /**
     * Get selected items from the request parameters including both current page's selection and session state
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<String> getSelected(ViewContext context)
    {
        return getSelected(context, null, true);
    }

    /**
     * Get selected items from the request parameters including both current page's selection and session state
     * @param context Used to get the selection key
     * @param clearSelection Remove the request parameter selected items from session selection state
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<String> getSelected(ViewContext context, boolean clearSelection)
    {
        return getSelected(context, null, clearSelection);
    }

    /**
     * Tests if selected items are in the request parameters or session state
     * @param context Used to get the selection key
     * @return true if there are selected item ids, false if not
     */
    public static boolean hasSelected(ViewContext context)
    {
        return !getSelected(context, null, false).isEmpty();
    }

    /**
     * Get selected items from the request parameters as integers including both current page's selection and session
     * state and clears the state
     * @param context Used to get the selection key
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<Long> getSelectedIntegers(ViewContext context)
    {
        return asLongs(getSelected(context, true));
    }

    /**
     * Get selected items from the request parameters as integers including both current page's selection and session state
     * @param context Used to get the selection key
     * @param clearSelection Remove the request parameter selected items from session selection state
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<Long> getSelectedIntegers(ViewContext context, boolean clearSelection)
    {
        return asLongs(getSelected(context, null, clearSelection));
    }

    @Nullable
    public static String getSelectionKeyFromRequest(ViewContext context)
    {
        HttpServletRequest request = context.getRequest();
        return request == null ? null : request.getParameter(DATA_REGION_SELECTION_KEY);
    }

    /**
     * Get the selected items from the request parameters (the current page of a data region) and session state.
     * @param context Contains the session
     * @param key The data region selection key; if null the DATA_REGION_SELECTION_KEY request parameter will be used
     * @param clearSession Remove the request parameter selected items from session selection state
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<String> getSelected(ViewContext context, @Nullable String key, boolean clearSession)
    {
        String[] values = null;
        var request = context.getRequest();
        if (request != null)
            values = context.getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME);
        if (null != values && values.length == 1 && values[0].contains("\t"))
            values = StringUtils.split(values[0],'\t');
        Set<String> result = values == null ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(values));

        Set<String> sessionSelected = getSet(context, key, false);
        synchronized (sessionSelected)
        {
            result.addAll(sessionSelected);
            if (clearSession)
                sessionSelected.removeAll(result);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Get the selected items from the request parameters (the current page of a data region) and session state as integers.
     */
    public static @NotNull Set<Long> getSelectedIntegers(ViewContext context, @Nullable String key, boolean clearSession)
    {
        return asLongs(getSelected(context, key, clearSession));
    }

    public static @NotNull ArrayList<String> getSnapshotSelected(ViewContext context, @Nullable String key)
    {
        return new ArrayList<>(getSet(context, key, false, true));
    }

    public static @NotNull ArrayList<Long> getSnapshotSelectedIntegers(ViewContext context, @Nullable String key)
    {
        return new LongArrayList(asLongs(getSnapshotSelected(context, key)));
    }

    private static @NotNull Set<Long> asLongs(Collection<String> ids)
    {
        Set<Long> result = new LinkedHashSet<>();
        for (String s : ids)
        {
            try
            {
                result.add(Long.parseLong(s));
            }
            catch (NumberFormatException nfe)
            {
                throw new BadRequestException("Unable to convert " + s + " to an int", nfe);
            }
        }

        return result;
    }

    public static int setSelected(ViewContext context, String key, Collection<String> selection, boolean checked)
    {
        return setSelected(context, key, selection, checked, false);
    }

    /**
     * Sets the checked state for the given ids in the session state.
     */
    public static int setSelected(ViewContext context, String key, Collection<String> selection, boolean checked, boolean useSnapshot)
    {
        return setSelected(context, key, selection, checked, useSnapshot, false);
    }

    private static int setSelected(
        ViewContext context,
        String key,
        Collection<String> selection,
        boolean checked,
        boolean useSnapshot,
        boolean replaceSelection
    )
    {
        if (checked && selection.size() > MAX_QUERY_SELECTION_SIZE)
            throw new BadRequestException(selectionTooLargeMessage(selection.size()));

        Set<String> selectedValues = getSet(context, key, true, useSnapshot);
        synchronized (selectedValues)
        {
            if (checked)
            {
                if (replaceSelection)
                {
                    selectedValues.clear();
                }
                else if (selectedValues.size() + selection.size() > MAX_QUERY_SELECTION_SIZE)
                {
                    // Verify that adding these selections will not result in a set that is too large
                    // Do not modify the actual selected values yet
                    int current = selectedValues.size();
                    int distinctAdds = 0;

                    for (String id : selection)
                    {
                        if (!selectedValues.contains(id))
                            distinctAdds++;
                    }

                    int prospective = current + distinctAdds;
                    if (prospective > MAX_QUERY_SELECTION_SIZE)
                        throw new BadRequestException(selectionTooLargeMessage(prospective));
                }

                selectedValues.addAll(selection);
            }
            else
                selectedValues.removeAll(selection);
        }

        return selectedValues.size();
    }

    public static int setSelectedFromForm(QueryForm form)
    {
        var view = getQueryView(form);
        var viewContext = view.getViewContext();
        var selection = getSet(viewContext, form.getQuerySettings().getSelectionKey(), true);
        var items = getSelectedItems(view, selection);

        return setSelected(viewContext, form.getQuerySettings().getSelectionKey(), items, false);
    }

    private static String selectionTooLargeMessage(long size)
    {
        return String.format("Too many selected items: %s. Maximum number of selected items allowed is %s.",
                Formats.commaf0.format(size), Formats.commaf0.format(MAX_QUERY_SELECTION_SIZE));
    }

    /**
     * Clear any session attributes that match the given container path, as the prefix, and the selection key, as the suffix
     */
    public static void clearRelatedByContainerPath(ViewContext context, String key)
    {
        if (key == null || context.getRequest() == null)
            return;

        HttpSession session = context.getRequest().getSession(false);
        String containerPath = context.getContainer().getPath();
        Collections.list(session.getAttributeNames()).stream()
            .filter(name -> name.startsWith(containerPath) && (name.endsWith(key + SNAPSHOT_SELECTED_VALUES) || name.endsWith(key + SELECTED_VALUES)))
            .forEach(session::removeAttribute);
    }

    private static void clearAll(HttpSession session, String path, String key, boolean isSnapshot)
    {
        assert path != null : "DataRegion container path required";
        assert key != null : "DataRegion selection key required";
        if (session == null)
            return;
        session.removeAttribute(getSessionAttributeKey(path, key, isSnapshot));
    }

    /**
     * Removes all selection state from the session for RenderContext.getSelectionKey().
     */
    public static void clearAll(RenderContext ctx)
    {
        clearAll(ctx.getRequest().getSession(false),
                ctx.getContainer().getPath(), ctx.getCurrentRegion().getSelectionKey(), false);
    }

    /**
     * Removes all selection state from the session for the given key. If key is null, the request parameter DATA_REGION_SELECTION_KEY is used.
     */
    public static void clearAll(ViewContext context, @Nullable String key)
    {
        clearAll(context, key, false);
    }

    public static void clearAll(ViewContext context, @Nullable String key, boolean isSnapshot)
    {
        HttpServletRequest request = context.getRequest();
        if (key == null)
            key = getSelectionKeyFromRequest(context);
        if (key != null && request != null)
            clearAll(request.getSession(false),
                context.getContainer().getPath(), key, isSnapshot);
    }

    /**
     * Removes all selection state from the session for the key given by request parameter DATA_REGION_SELECTION_KEY.
     */
    public static void clearAll(ViewContext context)
    {
        clearAll(context, null);
    }

    /**
     * Gets the ids of the selected items for all items in the given query form's view.  That is,
     * not just the items on the current page, but all selected items corresponding to the view's filters.
     */
    public static Set<String> getSelected(QueryForm form, boolean clearSelected) throws IOException
    {
        var view = getQueryView(form);
        var selection = getSet(view.getViewContext(), form.getQuerySettings().getSelectionKey(), true);
        var items = getSelectedItems(view, selection);

        if (clearSelected && !selection.isEmpty())
        {
            synchronized (selection)
            {
                items.forEach(selection::remove);
            }
        }

        return Collections.unmodifiableSet(items);
    }

    private static Pair<DataRegion, RenderContext> getDataRegionContext(QueryView view)
    {
        // Turn off features of QueryView
        view.setPrintView(true);
        view.setShowConfiguredButtons(false);
        view.setShowPagination(false);
        view.setShowPaginationCount(false);
        view.setShowDetailsColumn(false);
        view.setShowUpdateColumn(false);

        TableInfo table = view.getTable();
        if (table == null)
        {
            throw new NotFoundException("Could not find table");
        }

        DataView v = view.createDataView();
        DataRegion rgn = v.getDataRegion();

        // Include all rows. If only selected rows are included, it does not
        // respect filters.
        view.getSettings().setShowRows(ShowRows.ALL);
        view.getSettings().setOffset(Table.NO_OFFSET);

        RenderContext rc = v.getRenderContext();
        rc.setViewContext(view.getViewContext());
        rc.setCache(false);

        setDataRegionColumnsForSelection(rgn, rc, view, table);

        return Pair.of(rgn, rc);
    }

    private static @NotNull QueryView getQueryView(QueryForm form) throws NotFoundException
    {
        var schema = form.getSchema();
        if (schema == null)
            throw new NotFoundException();
        return schema.createView(form, null);
    }

    public static Set<String> getValidatedIds(@NotNull Collection<String> selection, QueryForm form)
    {
        return getSelectedItems(getQueryView(form), selection);
    }

    /**
     * Sets the selection for all items in the given query form's view
     */
    public static int setSelectionForAll(QueryForm form, boolean checked) throws IOException
    {
        return setSelectionForAll(getQueryView(form), form.getQuerySettings().getSelectionKey(), checked);
    }

    private static void setDataRegionColumnsForSelection(DataRegion rgn, RenderContext rc, QueryView view, TableInfo table)
    {
        // force the pk column(s) into the default list of columns
        List<String> selectorColNames = rgn.getRecordSelectorValueColumns();
        if (selectorColNames == null)
            selectorColNames = table.getPkColumnNames();
        List<ColumnInfo> selectorColumns = new ArrayList<>();
        for (String colName : selectorColNames)
        {
            if (null == rgn.getDisplayColumn(colName)) {
                selectorColumns.add(table.getColumn(colName));
            }
        }
        ActionURL url = view.getSettings().getSortFilterURL();

        Sort sort = rc.buildSort(table, url, rgn.getName());
        SimpleFilter filter = rc.buildFilter(table, rc.getColumnInfos(rgn.getDisplayColumns()), url, rgn.getName(), Table.ALL_ROWS, 0, sort);

        // Issue 36600: remove unnecessary columns for performance purposes
        rgn.clearColumns();
        // Issue 39011: then add back the columns needed by the filters, if any
        Collection<ColumnInfo> filterColumns = QueryService.get().ensureRequiredColumns(table, selectorColumns, filter, sort, null);
        rgn.addColumns(selectorColumns);
        rgn.addColumns(filterColumns);
    }

    public static int setSelectionForAll(QueryView view, String key, boolean checked) throws IOException
    {
        var regionCtx = getDataRegionContext(view);
        var rgn = regionCtx.first;
        var rc = regionCtx.second;

        try (Timing ignored = MiniProfiler.step("selectAll"); ResultSet rs = rgn.getResults(rc))
        {
            var selection = createSelectionSet(rc, rgn, rs, null);
            return setSelected(view.getViewContext(), key, selection, checked, false, true);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /**
     * Returns all items in the given result set that are selected and selectable
     * @param view the view from which to retrieve the data region context and session variable
     * @param selectedValues optionally (nullable) specify a collection of selected values that will be matched
     *                       against when selecting items. If null, then all items will be returned.
     * @return Set of items from the result set that are in the selected session, or an empty list if none.
     */
    private static Set<String> getSelectedItems(QueryView view, @NotNull Collection<String> selectedValues)
    {
        // Issue 48657: no need to query the region result set if we have no selectedValues
        if (selectedValues.isEmpty())
            return new LinkedHashSet<>();

        var dataRegionContext = getDataRegionContext(view);
        var rgn = dataRegionContext.first;
        var ctx = dataRegionContext.second;

        // Issue 48657: no need to query for all region results if we are only interested in a subset, filter for just those we want to verify
        // Note: this only currently applies for tables with a single PK col. Consider altering this for multi-pk tables.
        List<ColumnInfo> pkCols = rgn.getTable().getPkColumns();
        if (pkCols.size() == 1)
        {
            ColumnInfo pkCol = pkCols.getFirst();
            ctx.setBaseFilter(new SimpleFilter(pkCol.getFieldKey(), pkCol.isNumericType() ? selectedValues.stream().map(Integer::parseInt).toList() : selectedValues, CompareType.IN));
        }

        try (Timing ignored = MiniProfiler.step("getSelected"); Results rs = rgn.getResults(ctx))
        {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (selectedValues)
            {
                return createSelectionSet(ctx, rgn, rs, selectedValues);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private static Set<String> createSelectionSet(
        RenderContext ctx,
        DataRegion rgn,
        ResultSet rs,
        @Nullable Collection<String> selectedValues
    ) throws SQLException
    {
        Set<String> selected = new LinkedHashSet<>();

        if (rs != null)
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
            while (rs.next())
            {
                ctx.setRow(factory.getRowMap(rs));

                // Issue 35513: Don't select un-selectables
                if (rgn.isRecordSelectorEnabled(ctx))
                {
                    var value = rgn.getRecordSelectorValue(ctx);
                    if (selectedValues == null || selectedValues.contains(value))
                    {
                        selected.add(value);
                        if (selected.size() == MAX_QUERY_SELECTION_SIZE)
                            break;
                    }
                }
            }
        }

        return selected;
    }

    /** Response used from SelectAll, ClearAll, and similar APIs for bulk selecting/unselecting data rows */
    public static class SelectionResponse extends ApiSimpleResponse
    {
        public SelectionResponse(int count)
        {
            super("count", count);
        }
    }

    public interface DataSelectionKeyForm
    {
        String getDataRegionSelectionKey();
        void setDataRegionSelectionKey(String key);
    }
}
