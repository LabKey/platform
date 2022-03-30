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
package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.replace;

/**
 * Holds configuration information about a {@link QueryView}, to be used by a {@link ExportScriptFactory}.
 * User: adam
 * Date: Jan 27, 2009
 */
public abstract class ExportScriptModel
{
    private static final Logger LOG = LogHelper.getLogger(ExportScriptModel.class, "Exports script snippets for developers using client APIs");

    private final QueryView _view;

    public ExportScriptModel(QueryView view)
    {
        assert view != null;
        _view = view;
    }

    public String getInstallationName()
    {
        LookAndFeelProperties props = LookAndFeelProperties.getInstance(_view.getViewContext().getContainer());
        return props.getShortName();
    }

    public String getCreatedOn()
    {
        // Note: Rendering as text, not HTML
        return DateUtil.formatDateTime(_view.getContainer());
    }

    public String getBaseUrl()
    {
        AppProps props = AppProps.getInstance();
        return props.getBaseServerUrl() + props.getContextPath();
    }

    public String getSchemaName()
    {
        return _view.getSchema().getSchemaName();
    }

    public String getQueryName()
    {
        return _view.getSettings().getQueryName();
    }

    public String getFolderPath()
    {
        return _view.getContainer().getPath();
    }

    @Nullable
    public String getViewName()
    {
        return _view.getSettings().getViewName();
    }

    public String getExportText()
    {
        try
        {
            return getScriptExportText();
        }
        catch (Exception e)
        {
            LOG.error("Failed to export script in " + this.getClass().getName(), e);
            return "Error generating script export: " + e.getMessage();
        }
    }

    protected abstract String getScriptExportText();

    public abstract String getFilters();

    public String getColumns()
    {
        StringBuilder ret = new StringBuilder();
        String sep = "";
        for (DisplayColumn dc : getQueryView().getDisplayColumns())
        {
            if (dc.isQueryColumn())
            {
                ret.append(sep);
                ret.append(dc.getColumnInfo().getName());
                sep = ",";
            }
        }
        return ret.toString();
    }

    protected abstract String makeFilterExpression(String name, CompareType operator, String value);

    protected List<String> getFilterExpressions()
    {
        //R package wants filters like this:
        //   makefilter(c(name, operator, value), c(name, operator, value))
        //where 'name' is the column name, 'operator' is the string version of the operator
        //as defined in the Rlabkey package, and 'value' is the filter value.

        //load the sort/filter url into a new SimpleFilter
        //and iterate the clauses
        QueryView view = getQueryView();
        ArrayList<String> makeFilterExprs = new ArrayList<>();
        SimpleFilter filter = new SimpleFilter(view.getSettings().getSortFilterURL(), view.getDataRegionName());

        for (FilterClause filterClause : filter.getClauses())
        {
            if (!(filterClause instanceof CompareType.AbstractCompareClause))
                throw new UnsupportedOperationException("Filter clause '" + filterClause.getClass().getName() + "' not currently supported in export scripts");

            CompareType.AbstractCompareClause clause = (CompareType.AbstractCompareClause)filterClause;

            //all filter clauses can report col names and values,
            //each of which in this case should contain only one value
            FieldKey fieldKey = clause.getFieldKey();
            CompareType operator = clause.getCompareType();
            var param = clause.toURLParam("q");
            var value = param == null ? null : param.getValue();

            makeFilterExprs.add(makeFilterExpression(fieldKey.toString(), operator, value));
        }

        return makeFilterExprs;
    }


    public boolean hasSort()
    {
        return null != getSort();
    }

    @Nullable
    public String getSort()
    {
        String sortParam = _view.getSettings().getSortFilterURL().getParameter(_view.getDataRegionName() +  ".sort");

        if (null == sortParam || sortParam.length() == 0)
            return null;
        else
            return sortParam;
    }

    public boolean hasQueryParameters()
    {
        return !_view.getSettings().getQueryParameters().isEmpty();
    }

    public @NotNull Map<String, String> getQueryParameters()
    {
        // Convert Map<String, Object> to Map<String, String>
        return _view.getSettings().getQueryParameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }

    protected QueryView getQueryView()
    {
        return _view;
    }

    public static ModelAndView getExportScriptView(QueryView queryView, String scriptType, PageConfig pageConfig, HttpServletResponse response)
    {
        if (scriptType == null)
            throw new IllegalArgumentException("You must pass a value for the 'scriptType' parameter!");

        pageConfig.setTemplate(PageConfig.Template.None);
        response.setContentType("text/plain");
        ExportScriptFactory factory = QueryView.getExportScriptFactory(scriptType);
        if (factory == null)
            throw new NotFoundException("Export script type not found: " + scriptType);

        WebPartView scriptView = new JspView<>("/org/labkey/api/query/createExportScript.jsp", factory.getModel(queryView));
        scriptView.setFrame(WebPartView.FrameType.NOT_HTML);

        QueryService.get().addAuditEvent(queryView, "Exported to script type " + scriptType, null);
        return scriptView;
    }

    public boolean hasContainerFilter()
    {
        ContainerFilter cFilter = getContainerFilter();
        return null != cFilter && null != cFilter.getType();
    }

    /**
     * Always test with hasContainerFilter() before calling this
     * @return Name of the ContainerFilter.Type
     */
    public @NotNull String getContainerFilterTypeName()
    {
        ContainerFilter cFilter = getContainerFilter();

        if (null == cFilter || null == cFilter.getType())
            throw new IllegalStateException("Must call hasContainerFilter() before calling getContainerFilterTypeName()");

        return cFilter.getType().name();
    }

    private ContainerFilter getContainerFilter()
    {
        String containerFilterName = _view.getSettings().getSortFilterURL().getParameter(_view.getDataRegionName() + DataRegion.CONTAINER_FILTER_NAME);

        if (containerFilterName != null)
            return ContainerFilter.getContainerFilterByName(containerFilterName, _view.getContainer(), _view.getUser());
        else
            return _view.getQueryDef().getContainerFilter();
    }

    protected String doubleQuote(String s)
    {
        if (null == s)
            s = "";
        s = replace(s, "\\", "\\\\");
        s = replace(s, "\"","\\\"");
        return "\"" + s + "\"";
    }

    protected String indent(int indentSpaces)
    {
        return StringUtils.repeat(" ", indentSpaces);
    }
}
