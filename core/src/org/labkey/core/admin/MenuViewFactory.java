/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;

public class MenuViewFactory
{
    private static final int MAX_PER_COLUMN = 20;

    public static WebPartView<?> createMenuQueryView(ViewContext context, String title, final CustomizeMenuForm form)
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
            settings.setReportId(null); // Issue 46238

            settings.setShowRows(ShowRows.PAGINATED);
            settings.setMaxRows(100);
            settings.setViewName(form.getViewName());

            QueryView view = new QueryView(schema, settings, null)
            {
                @Override
                protected void renderDataRegion(HtmlWriter out)
                {
                    DIV(
                        at(style, "max-width: 40vw; overflow-x: auto;"),
                        TABLE(
                            (Renderable) ret -> {
                                ArrayList<Renderable> renderables = new ArrayList<>();
                                TableInfo tableInfo = getTable();
                                if (null != tableInfo)
                                {
                                    var columnInfo = (BaseColumnInfo)tableInfo.getColumn(form.getColumnName());
                                    String urlBase = form.getUrl();
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

                                    if (urlBase != null && !urlBase.contentEquals(""))
                                        dataColumn.setURLExpression(StringExpressionFactory.createURL(form.getUrl()));

                                    RenderContext renderContext = new RenderContext(actualContext);

                                    try (Results results = getResults(ShowRows.PAGINATED))
                                    {
                                        renderContext.setResults(results);
                                        ResultSet rs = results.getResultSet();
                                        if (null != rs)
                                        {
                                            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                                            // Enumerate the ResultSet and build a list of Renderables that can be rendered in any order
                                            while (rs.next())
                                            {
                                                Map<String, Object> map = factory.getRowMap(rs);
                                                renderables.add(ret2 -> {
                                                    renderContext.setRow(map);
                                                    dataColumn.renderGridCellContents(renderContext, out);
                                                    return ret2;
                                                });
                                            }
                                        }
                                    }
                                    catch (SQLException e)
                                    {
                                        throw new RuntimeSQLException(e);
                                    }
                                    catch (IOException e)
                                    {
                                        throw UnexpectedException.wrap(e);
                                    }
                                }

                                if (renderables.isEmpty())
                                {
                                    TR(
                                        TD(
                                            "No query results."
                                        )
                                    ).appendTo(out);
                                }
                                else
                                {
                                    writeCells(renderables, out);
                                }

                                return ret;
                            }
                        )
                    ).appendTo(out);
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
            return new WebPartView<>(title) {
                @Override
                protected void renderView(Object model, HtmlWriter out)
                {
                    TABLE(TR(TD(
                        at(style, "vertical-align:top;padding:4px;white-space:pre;"),
                        "No schema or query selected."
                    ))).appendTo(out);
                }
            };
        }
    }

    public static WebPartView<?> createMenuFolderView(final ViewContext context, String title, final CustomizeMenuForm form)
    {
        // If rootPath is "", then use current context's container
        String rootPath = form.getRootFolder();
        Container rootFolder = StringUtils.isBlank(rootPath) ? context.getContainer() : ContainerManager.getForPath(rootPath);
        final User user = context.getUser();
        List<Container> containersTemp;
        if (null != rootFolder)
        {
            if (form.isIncludeAllDescendants())
            {
                containersTemp = ContainerManager.getAllChildren(rootFolder, user, ReadPermission.class, NormalContainerType.NAME);
                containersTemp.remove(rootFolder);      // getAllChildren adds root, which we don't want
            }
            else
            {
                containersTemp = ContainerManager.getChildren(rootFolder, user, ReadPermission.class, NormalContainerType.NAME);
    //            containersTemp.add(rootFolder);      // Don't add root folder; later we may add a checkbox to allow it to be added, if so, check root's permissions
            }
        }
        else
        {
            containersTemp = new ArrayList<>();
        }

        if (!context.getContainer().hasPermission(user, AdminPermission.class))
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

        WebPartView<?> view = new WebPartView<>(title) {
            @Override
            protected void renderView(Object model, HtmlWriter out)
            {
                final String filterFolderName = form.getFolderTypes();
                StringExpression expr;
                String urlBase = form.getUrl();

                if (null != StringUtils.trimToNull(urlBase))
                {
                    expr = StringExpressionFactory.createURL(form.getUrl());
                }
                else
                {
                    expr = null;
                }

                MutableBoolean seenAtLeastOne = new MutableBoolean(false);
                DIV(
                    at(style, "max-width: 40vw; overflow-x: auto;"),
                    TABLE(
                        (Renderable) ret -> {
                            ArrayList<Renderable> renderables = new ArrayList<>();
                            for (Container container : containers)
                            {
                                if (null == StringUtils.trimToNull(filterFolderName) ||
                                    "[all]".equals(filterFolderName) ||
                                    container.getFolderType().getName().equals(filterFolderName))
                                {
                                    ActionURL actionURL;
                                    if (null != expr)
                                    {
                                        try
                                        {
                                            actionURL = new ActionURL(expr.getSource());
                                            actionURL.setContainer(container);
                                        }
                                        catch (IllegalArgumentException e)
                                        {
                                            throw new ApiUsageException("Invalid source URL", e);
                                        }
                                    }
                                    else
                                    {
                                        actionURL = container.getStartURL(user);
                                    }

                                    String uri = actionURL.getLocalURIString();
                                    if (null != StringUtils.trimToNull(uri))
                                    {
                                        String name = null != StringUtils.trimToNull(container.getName()) ? container.getName() : "[root]";
                                        renderables.add(LinkBuilder.simpleLink(name, uri));
                                        seenAtLeastOne.setTrue();
                                    }
                                }
                            }

                            if (renderables.isEmpty())
                            {
                                TR(
                                    TD(
                                        at(style, "vertical-align:top;padding:4px;white-space:pre;"),
                                        "No folders selected."
                                    )
                                ).appendTo(out);
                            }
                            else
                            {
                                writeCells(renderables, out);
                            }

                            return ret;
                        }
                    )
                ).appendTo(out);
            }
        };

        view.setEmpty(containers.isEmpty());
        return view;
    }

    // Renders renderables in columns of max length MAX_PER_COLUMN. renderables must be non-empty and renderable in any order.
    private static void writeCells(ArrayList<Renderable> renderables, HtmlWriter out)
    {
        List<List<Renderable>> lists = Lists.partition(renderables, MAX_PER_COLUMN);
        int columns = lists.size();
        int rows = lists.getFirst().size();
        for (int i = 0; i < rows; i++)
        {
            int idx = i;
            TR(
                (Renderable) ret -> {
                    for (int j = 0; j < columns; j++)
                    {
                        List<Renderable> list = lists.get(j);
                        if (list.size() <= idx)
                            break;

                        TD(
                            at(style, "vertical-align:top;padding:0px 4px;white-space:pre;"),
                            list.get(idx)
                        ).appendTo(out);
                    }
                    return ret;
                }
            ).appendTo(out);
        }
    }
}
