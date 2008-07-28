/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.view.ViewContext;
import org.labkey.api.settings.AppProps;

import javax.servlet.http.HttpSession;
import java.util.*;

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
            Set<String> result = (Set<String>)session.getAttribute(key);
            if (result == null && create)
            {
                result = new LinkedHashSet<String>();
                session.setAttribute(key, result);
            }
            return result;
        }
        return Collections.emptySet();
    }

    /**
     * Composes a selection key string used to uniquely identify the selected items
     * of a given dataregion.  Nulls are allowed.
     */
    public static String getSelectionKey(String schemaName, String queryName, String viewName, String dataRegionName)
    {
        StringBuffer buf = new StringBuffer();
        for (String s : new String[]{schemaName, queryName, viewName, dataRegionName})
        {
            buf.append(SEPARATOR);
            if (s != null)
                buf.append(s);
        }
        return buf.toString();
    }


    /**
     * Get selected items from the request parameters (the current page of a data region).
     * @param context Used to get the selection key
     * @param clearSelection Remove the request parameter selected items from session selection state
     * @return an unmodifiable copy of the selected item ids
     */
    public static Set<String> getSelected(ViewContext context, boolean clearSelection)
    {
        return getSelected(context, null, false, clearSelection);
    }

    public static String getSelectionKeyFromRequest(ViewContext context)
    {
        String selectionKey = context.getRequest().getParameter(DATA_REGION_SELECTION_KEY);
        assert !(AppProps.getInstance().isDevMode() && selectionKey == null) : "Could not find " + DATA_REGION_SELECTION_KEY + " in request parameters";
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
    public static Set<String> getSelected(ViewContext context, String key, boolean mergeSession, boolean clearSession)
    {
        Set<String> result = new LinkedHashSet<String>();
        String[] values = context.getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME);
        List<String> parameterSelected = values == null ? new ArrayList<String>() : Arrays.asList(values);
        result.addAll(parameterSelected);

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
                        sessionSelected.removeAll(parameterSelected);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Sets the checked state for the given ids in the session state.
     */
    public static int setSelected(ViewContext context, String key, String[] ids, boolean checked)
    {
        synchronized (lock)
        {
            Set<String> selectedValues = getSet(context, key, true);
            List<String> select = new ArrayList<String>();
            if (ids != null)
            {
                for (String id : ids)
                {
                    if (StringUtils.isNotBlank(id))
                        select.add(id);
                }
            }

            if (checked)
                selectedValues.addAll(select);
            else
                selectedValues.removeAll(select);
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
     * Removes all selection state from the session for the given key.
     */
    public static void clearAll(ViewContext context, String key)
    {
        if (key == null)
            key = getSelectionKeyFromRequest(context);
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

    public interface DataSelectionKeyForm
    {
        public String getDataRegionSelectionKey();
        public void setDataRegionSelectionKey(String key);
    }
}
