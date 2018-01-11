/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: kevink
 * Date: Jan 3, 2008 4:16:06 PM
 */
public class DataRegionSelection
{
    public static final String SELECTED_VALUES = ".selectValues";
    public static final String SEPARATOR = "$";
    public static final String DATA_REGION_SELECTION_KEY = "dataRegionSelectionKey";
    private static final Object lock = new Object();

    private static Set<String> getSet(ViewContext context, String key, boolean create)
    {
        if (key == null)
            key = getSelectionKeyFromRequest(context);

        if (key != null)
        {
            key = context.getContainer().getPath() + key + SELECTED_VALUES;
            HttpSession session = context.getRequest().getSession(false);
            if (session != null)
            {
                @SuppressWarnings("unchecked") Set<String> result = (Set<String>)session.getAttribute(key);
                if (result == null && create)
                {
                    result = new LinkedHashSet<>();
                    session.setAttribute(key, result);
                }
                return result;
            }
        }
        return new LinkedHashSet<>();
    }

    /**
     * Composes a selection key string used to uniquely identify the selected items
     * of a given dataregion.  Nulls are allowed.
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
     * Get selected items from the request parameters (includes only the current page of a data region and no selected items from session state).
     * @param context Used to get the selection key
     * @param clearSelection Remove the request parameter selected items from session selection state
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<String> getSelected(ViewContext context, boolean clearSelection)
    {
        return getSelected(context, null, false, clearSelection);
    }

    /**
     * Tests if selected items are in the request parameters (includes only the current page of a data region and no selected items from session state).
     * @param context Used to get the selection key
     * @return true if there are selected item ids, false if not
     */
    public static boolean hasSelected(ViewContext context)
    {
        return !getSelected(context, null, false, false).isEmpty();
    }

    /**
     * Get selected items from the request parameters as integers (includes only the current page of a data region and no selected items from session state).
     * @param context Used to get the selection key
     * @param clearSelection Remove the request parameter selected items from session selection state
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<Integer> getSelectedIntegers(ViewContext context, boolean clearSelection)
    {
        Set<Integer> result = new LinkedHashSet<>();

        for (String s : getSelected(context, null, false, clearSelection))
            result.add(Integer.parseInt(s));

        return result;
    }

    public static String getSelectionKeyFromRequest(ViewContext context)
    {
        String selectionKey = context.getRequest().getParameter(DATA_REGION_SELECTION_KEY);
        if (AppProps.getInstance().isDevMode() && selectionKey == null)
        {
            throw new NotFoundException("Could not find " + DATA_REGION_SELECTION_KEY + " in request parameters");
        }
        return selectionKey;
    }

    /**
     * Get the selected items from the request parameters (the current page of a data region) and session state.
     * @param context Contains the session
     * @param key The data region selection key; if null the DATA_REGION_SELECTION_KEY request parameter will be used
     * @param mergeSession false will only get the selection from the request parameters, true will get add the selection in session state
     * @param clearSession Remove the request parameter selected items from session selection state
     * @return an unmodifiable copy of the selected item ids
     */
    public static @NotNull Set<String> getSelected(ViewContext context, @Nullable String key, boolean mergeSession, boolean clearSession)
    {
        String[] values = context.getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME);
        List<String> parameterSelected = values == null ? new ArrayList<>() : Arrays.asList(values);
        Set<String> result = new LinkedHashSet<>(parameterSelected);

        if (mergeSession || clearSession)
        {
            synchronized (lock)
            {
                Set<String> sessionSelected = getSet(context, key, false);
                if (sessionSelected != null)
                {
                    if (mergeSession)
                        result.addAll(sessionSelected);

                    if (clearSession)
                        sessionSelected.removeAll(result);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Sets the checked state for the given ids in the session state.
     */
    public static int setSelected(ViewContext context, String key, Collection<String> selection, boolean checked)
    {
        synchronized (lock)
        {
            Set<String> selectedValues = getSet(context, key, true);

            if (checked)
                selectedValues.addAll(selection);
            else
                selectedValues.removeAll(selection);
            return selectedValues.size();
        }
    }

    private static void clearAll(HttpSession session, String path, String key)
    {
        assert key != null : "DataRegion selection key required";
        if (session == null)
            return;
        synchronized (lock)
        {
            session.removeAttribute(path + key + SELECTED_VALUES);
        }
    }


    /**
     * Removes all selection state from the session for RenderContext.getSelectionKey().
     */
    public static void clearAll(RenderContext ctx)
    {
        clearAll(ctx.getRequest().getSession(false),
                ctx.getContainer().getPath(), ctx.getCurrentRegion().getSelectionKey());
    }


    /**
     * Removes all selection state from the session for the given key. If key is null, the request parameter DATA_REGION_SELECTION_KEY is used.
     */
    public static void clearAll(ViewContext context, @Nullable String key)
    {
        if (key == null)
            key = getSelectionKeyFromRequest(context);
        if (key != null)
            clearAll(context.getRequest().getSession(false),
                context.getContainer().getPath(), key);
    }


    /**
     * Removes all selection state from the session for the key given by request parameter DATA_REGION_SELECTION_KEY.
     */
    public static void clearAll(ViewContext context)
    {
        clearAll(context, null);
    }


    public static int selectAll(QueryForm form) throws IOException
    {
        UserSchema schema = form.getSchema();
        if (schema == null)
            throw new NotFoundException();

        QueryView view = schema.createView(form, null);
        return selectAll(view, form.getQuerySettings().getSelectionKey());
    }

    public static int selectAll(QueryView view, String key) throws IOException
    {
        ViewContext context = view.getViewContext();

        TableInfo table = view.getTable();

        DataView v = view.createDataView();
        DataRegion rgn = v.getDataRegion();
        rgn.setShowPaginationCount(false);

        // Include all rows
        rgn.getSettings().setShowRows(ShowRows.ALL);
        rgn.getSettings().setOffset(Table.NO_OFFSET);

        //force the pk column(s) into the default list of columns
        List<String> colNames = rgn.getRecordSelectorValueColumns();
        if (colNames == null)
            colNames = table.getPkColumnNames();
        for (String colName : colNames)
        {
            if (null == rgn.getDisplayColumn(colName))
                rgn.addColumns(table, colName);
        }

        RenderContext rc = v.getRenderContext();
        rc.setCache(false);

        try (ResultSet rs = rgn.getResultSet(rc))
        {
            List<String> selection = createSelectionList(rc, rgn, rs, colNames);
            return setSelected(context, key, selection, true);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private static List<String> createSelectionList(RenderContext ctx, DataRegion rgn, ResultSet rs, List<String> colNames) throws SQLException
    {
        List<String> selected = new LinkedList<>();

        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
        while (rs.next())
        {
            ctx.setRow(factory.getRowMap(rs));
            String value = rgn.getRecordSelectorValue(ctx);
            selected.add(value);
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
