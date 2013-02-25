/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.core.admin;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.StringBuilderWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MenuViewFactory
{
    private static final int MAX_PER_COLUMN = 15;

    public static WebPartView createMenuQueryView(ViewContext context, String title, final CustomizeMenuForm form)
    {
        if (null != StringUtils.trimToNull(form.getFolderName()))
        {
            Container container = ContainerManager.getForPath(form.getFolderName());
            context = new ViewContext(context);
            context.setContainer(container);        // Need ViewComntext with proper container
        }

        final ViewContext actualContext = context;
        String schemaName = StringUtils.trimToNull(form.getSchemaName());
        if (null != schemaName)
        {
            UserSchema schema = QueryService.get().getUserSchema(actualContext.getUser(), actualContext.getContainer(), schemaName);
            if (null == schema)
                throw new IllegalArgumentException("Schema '" + schemaName + "' could not be found.");

            QuerySettings settings = new QuerySettings(actualContext, null, form.getQueryName());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            settings.setShowRows(ShowRows.PAGINATED);
            settings.setMaxRows(100);
            settings.setViewName(form.getViewName());

            QueryView view = new QueryView(schema, settings, null)
            {
                @Override
                protected void renderDataRegion(PrintWriter out) throws Exception
                {
                    boolean seenAtLeastOne = false;
                    out.write("<table style='width:50'>");
                    TableInfo tableInfo = getTable();
                    if (null != tableInfo)
                    {
                        ColumnInfo columnInfo = tableInfo.getColumn(form.getColumnName());
                        String urlBase = form.getUrl();
                        if (urlBase != null && !urlBase.contentEquals(""))
                            columnInfo.setURL(StringExpressionFactory.createURL(form.getUrl()));
                        DataColumn dataColumn = new DataColumn(columnInfo, false)
                        {
                            @Override           // so we can use DetailsURL if no other URL can be used
                            protected String renderURLorValueURL(RenderContext renderContext)
                            {
                                String url = super.renderURLorValueURL(renderContext);
                                if (null == url)
                                {
                                    StringExpression expr = getColumnInfo().getParentTable().getDetailsURL(null, renderContext.getContainer());
                                    if (null != expr)
                                        url = expr.eval(renderContext);
                                }
                                return url;
                            }
                        };

                        RenderContext renderContext = new RenderContext(actualContext);
                        Results results = getResults(ShowRows.PAGINATED);
                        try
                        {
                            renderContext.setResults(results);
                            ResultSet rs = results.getResultSet();
                            if (null != rs)
                            {
                                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                                // To do columns, we'll write each cell into a StringBuilder, then we have the count and can go from there
                                ArrayList<StringBuilder> cellStrings = new ArrayList<StringBuilder>();
                                while (rs.next())
                                {
                                    StringBuilder stringBuilder = new StringBuilder();
                                    cellStrings.add(stringBuilder);
                                    StringBuilderWriter writer = new StringBuilderWriter(stringBuilder);
                                    renderContext.setRow(factory.getRowMap(rs));
                                    dataColumn.renderGridCellContents(renderContext, writer);
                                    seenAtLeastOne = true;
                                }

                                writeCells(cellStrings, out);
                            }

                        }
                        finally
                        {
                            ResultSetUtil.close(results);
                        }
                    }
                    if (!seenAtLeastOne)
                        out.write("<tr><td>No query results.</td></tr>");
                    out.write("</table>");
                }
            };
            view.setTitle(title);

            view.setShowBorders(false);
            view.setShowConfiguredButtons(false);
            view.setShowDeleteButton(false);
            view.setShowDetailsColumn(false);
            view.setShowExportButtons(false);
            view.setShowFilterDescription(false);
            view.setShowImportDataButton(false);
            view.setShowInsertNewButton(false);
            view.setShowPaginationCount(false);
            view.setAllowExportExternalQuery(false);
            view.setShowSurroundingBorder(false);
            view.setShowPaginationCount(false);
            view.setShowPagination(false);
            return view;
        }
        else
        {
            WebPartView view = new WebPartView(title) {
                @Override
                protected void renderView(Object model, PrintWriter out) throws Exception
                {
                    out.write("<table style='width:50'><tr><td style='vertical-align:top;padding:4px'>");
                    out.write("No schema or query selected.");
                    out.write("</td></tr></table>");
                }
            };
            return view;
        }
    }

    private static String getNextUniqueId()
    {
        return ((Long)System.currentTimeMillis()).toString();
    }

    public static WebPartView createMenuFolderView(final ViewContext context, String title, final CustomizeMenuForm form)
    {
        // If rootPath is "", then use current context's container
        String rootPath = form.getRootFolder();
        MenuWebPartFolderForm menuWebPartFolderForm = new MenuWebPartFolderForm();
        menuWebPartFolderForm.setRootPath(rootPath);
        menuWebPartFolderForm.setIncludeChildren(form.isIncludeAllDescendants());
        menuWebPartFolderForm.setFilterType(form.getFolderTypes());
        menuWebPartFolderForm.setUrlBase(form.getUrl());
        menuWebPartFolderForm.setUniqueId(getNextUniqueId());

        JSONObject containerTree = getContainerTree(context, menuWebPartFolderForm);
        if (containerTree.containsKey("children") && containerTree.getJSONArray("children").length() > 0)
        {
            // Not empty
            JspView<MenuWebPartFolderForm> menuView = new JspView<MenuWebPartFolderForm>("/org/labkey/core/admin/menuWebPartFolder.jsp", menuWebPartFolderForm);
            VBox vbox = new VBox(menuView);
            vbox.setTitle(title);
            return vbox;
        }
        else
        {
            WebPartView view = new WebPartView(title) {
                @Override
                protected void renderView(Object model, PrintWriter out) throws Exception
                {
                    out.write("<table style='width:50'>");
                    out.write("<tr><td style='vertical-align:top;padding:4px'>No folders selected.</td></tr>");
                    out.write("</table>");
                }
            };

            view.setEmpty(true);
            return view;
        }
    }

    private static void writeCells(ArrayList<StringBuilder> cells, PrintWriter out)
    {
        int countContainers = cells.size();
        int countColumns = (int)Math.ceil((double)countContainers/MAX_PER_COLUMN);
        int countRows =  (int)Math.ceil((double)countContainers/countColumns);

        for (int i = 0; i < countRows; i += 1)
        {
            out.write("<tr>");

            for (int k = 0; k < countColumns; k += 1)
            {
                int index = k * countRows + i;
                if (index < cells.size())
                {
                    StringBuilder cell = cells.get(index);
                    out.write("<td style='vertical-align:top;padding:0px 4px'>");
                    out.write(cell.toString());
                    out.write("</td>");
                }
            }
            out.write("</tr>");
        }
    }

    public static class MenuWebPartFolderForm
    {
        private String _rootPath;
        private String _filterType;
        private String _urlBase;
        private boolean _includeChildren;
        private String _uniqueId;

        public String getRootPath()
        {
            return _rootPath;
        }

        public void setRootPath(String rootPath)
        {
            _rootPath = rootPath;
        }

        public String getFilterType()
        {
            return _filterType;
        }

        public void setFilterType(String filterType)
        {
            _filterType = filterType;
        }

        public boolean isIncludeChildren()
        {
            return _includeChildren;
        }

        public void setIncludeChildren(boolean includeChildren)
        {
            _includeChildren = includeChildren;
        }

        public String getUrlBase()
        {
            return _urlBase;
        }

        public void setUrlBase(String urlBase)
        {
            _urlBase = urlBase;
        }

        public String getUniqueId()
        {
            return _uniqueId;
        }

        public void setUniqueId(String uniqueId)
        {
            _uniqueId = uniqueId;
        }
    }

    public static JSONObject getContainerTree(ViewContext context, MenuWebPartFolderForm form)
    {
        Container rootFolder = (0 == form.getRootPath().compareTo("")) ? context.getContainer() : ContainerManager.getForPath(form.getRootPath());

        StringExpression expr = null;
        String urlBase = form.getUrlBase();
        if (null != StringUtils.trimToNull(urlBase))
        {
            expr = StringExpressionFactory.createURL(form.getUrlBase());
        }

        JSONObject props = null;
        if (null != rootFolder)
        {
            props = getContainerProps(rootFolder, context, form.getFilterType(), expr, form.isIncludeChildren(), true);
        }
        if (null == props)
            props = new JSONObject();

        return props;
    }

    private static JSONObject getContainerProps(Container container, ViewContext context, String filterFolderName,
                                                StringExpression urlBase, boolean includeChildren, boolean isRoot)
    {
        JSONObject props = null;

        ActionURL actionURL = null;
        if (null != urlBase)
        {
            actionURL = new ActionURL(urlBase.getSource());
            actionURL.setContainer(container);
        }
        else
        {
            actionURL = container.getStartURL(context.getUser());
        }

        String uri = actionURL.getLocalURIString();
        if (null != StringUtils.trimToNull(uri))
        {
            String name = null != StringUtils.trimToNull(container.getName()) ? container.getName() : "[root]";
            String text = "<a href=\"" + uri + "\">" + PageFlowUtil.filter(name) + "</a>";
            props = new JSONObject();
            props.put("text", text);
            props.put("isProject", container.isProject());

            List<Container> containersTemp = null;
            if (isRoot || includeChildren)
            {
                containersTemp = ContainerManager.getChildren(container, context.getUser(), ReadPermission.class, false);   // no workbooks
            }

            JSONArray childrenProps = new JSONArray();
            if (null != containersTemp)
            {
                if (!context.getContainer().getPolicy().hasPermission(context.getUser(), AdminPermission.class))
                {
                    // If user doesn't have Admin permission, don't show "_" containers
                    List<Container> adjustedContainers = new ArrayList<Container>();
                    for (Container container1 : containersTemp)
                    {
                        if (!container.getName().startsWith("_"))
                            adjustedContainers.add(container1);
                    }
                    containersTemp = adjustedContainers;
                }

                Collections.sort(containersTemp, new Comparator<Container>()
                {
                    @Override
                    public int compare(Container container1, Container container2)
                    {
                        return container1.getName().compareToIgnoreCase(container2.getName());
                    }
                });

                for (Container child : containersTemp)
                {
                    JSONObject childProps = getContainerProps(child, context, filterFolderName, urlBase, includeChildren, false);
                    if (null != childProps)
                        childrenProps.put(childProps);
                }
            }
            props.put("children", childrenProps);

            // Check folder filter here; keep node if it passes filter OR if it has children (because they've passed the filter already)
            if (!(
                    null == StringUtils.trimToNull(filterFolderName) ||
                    "[all]".equals(filterFolderName) ||
                    container.getFolderType().getName().equals(filterFolderName) ||
                    props.getJSONArray("children").length() > 0))
            {
                props = null;
            }
        }

        return props;
    }

}
