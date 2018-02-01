/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: davebradlee
 * Date: 10/17/12
 * Time: 3:18 PM
 */
public class MenuViewFactory
{
    private static final int MAX_PER_COLUMN = 20;

    public static WebPartView createMenuQueryView(ViewContext context, String title, final CustomizeMenuForm form)
    {
        if (null != StringUtils.trimToNull(form.getFolderName()))
        {
            Container container = ContainerManager.getForPath(form.getFolderName());
            context = new ViewContext(context);
            context.setContainer(container);        // Need ViewContext with proper container
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
                    out.write("<div style=\"max-width: 40vw; overflow-x: auto;\">");
                    out.write("<table>");
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

                        try (Results results = getResults(ShowRows.PAGINATED))
                        {
                            renderContext.setResults(results);
                            ResultSet rs = results.getResultSet();
                            if (null != rs)
                            {
                                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                                // To do columns, we'll write each cell into a StringBuilder, then we have the count and can go from there
                                ArrayList<StringBuilder> cellStrings = new ArrayList<>();
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
                    }
                    if (!seenAtLeastOne)
                        out.write("<tr><td>No query results.</td></tr>");
                    out.write("</table></div>");
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
                    out.write("<table><tr><td style='vertical-align:top;padding:4px;white-space:pre;'>");
                    out.write("No schema or query selected.");
                    out.write("</td></tr></table>");
                }
            };
            return view;
        }
    }

    public static WebPartView createMenuFolderView(final ViewContext context, String title, final CustomizeMenuForm form)
    {
        // If rootPath is "", then use current context's container
        String rootPath = form.getRootFolder();
        Container rootFolder = (0 == rootPath.compareTo("")) ? context.getContainer() : ContainerManager.getForPath(rootPath);
        final User user = context.getUser();
        List<Container> containersTemp;
        if (null != rootFolder)
        {
            if (form.isIncludeAllDescendants())
            {
                containersTemp = ContainerManager.getAllChildren(rootFolder, user, ReadPermission.class, false);    // no workbooks
                containersTemp.remove(rootFolder);      // getAllChildren adds root, which we don't want
            }
            else
            {
                containersTemp = ContainerManager.getChildren(rootFolder, user, ReadPermission.class, false);   // no workbooks
    //            containersTemp.add(rootFolder);      // Don't add root folder; later we may add a checkbox to allow it to be added, if so, check root's permissions
            }
        }
        else
        {
            containersTemp = new ArrayList<>();
        }

        if (!context.getContainer().getPolicy().hasPermission(user, AdminPermission.class))
        {
            // If user doesn't have Admin permission, don't show "_" containers
            List<Container> adjustedContainers = new ArrayList<>();
            for (Container container : containersTemp)
            {
                if (!container.getName().startsWith("_"))
                    adjustedContainers.add(container);
            }
            containersTemp = adjustedContainers;
        }

        Collections.sort(containersTemp);

        final Collection<Container> containers = containersTemp;

        WebPartView view = new WebPartView(title) {
            @Override
            protected void renderView(Object model, PrintWriter out) throws Exception
            {
                final String filterFolderName = form.getFolderTypes();
                StringExpression expr = null;
                String urlBase = form.getUrl();
                if (null != StringUtils.trimToNull(urlBase))
                {
                    expr = StringExpressionFactory.createURL(form.getUrl());
                }

                boolean seenAtLeastOne = false;
                out.write("<div style=\"max-width: 40vw; overflow-x: auto;\">");
                out.write("<table>");
                ArrayList<StringBuilder> cells = new ArrayList<>();
                for (Container container : containers)
                {
                    if (null == StringUtils.trimToNull(filterFolderName) ||
                            "[all]".equals(filterFolderName) ||
                            container.getFolderType().getName().equals(filterFolderName))
                    {
                        ActionURL actionURL;
                        if (null != expr)
                        {
                            actionURL = new ActionURL(expr.getSource());
                            actionURL.setContainer(container);
                        }
                        else
                        {
                            actionURL = container.getStartURL(user);
                        }

                        String uri = actionURL.getLocalURIString();
                        if (null != StringUtils.trimToNull(uri))
                        {
                            String name = null != StringUtils.trimToNull(container.getName()) ? container.getName() : "[root]";
                            StringBuilder cell = new StringBuilder("<a href=\"" + uri + "\">" + name + "</a>");
                            cells.add(cell);
                            seenAtLeastOne = true;
                        }
                    }
                }

                writeCells(cells, out);

                if (!seenAtLeastOne)
                    out.write("<tr><td style='vertical-align:top;padding:4px;white-space:pre;'>No folders selected.</td></tr>");
                out.write("</table></div>");
            }
        };

        view.setEmpty(containers.isEmpty());
        return view;
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
                    out.write("<td style='vertical-align:top;padding:0px 4px;white-space:pre;'>");
                    out.write(cell.toString());
                    out.write("</td>");
                }
            }
            out.write("</tr>");
        }
    }


}
