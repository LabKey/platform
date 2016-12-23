/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.Errors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A view backed by a {@link DataRegion}, which is almost always bound to a source table in the database.
 */
public abstract class DataView extends WebPartView<RenderContext>
{
    private DataRegion _dataRegion = null;

    private static final Logger _log = Logger.getLogger(DataView.class);

    // Call this constructor if you need to subclass the RenderContext
    public DataView(DataRegion dataRegion, RenderContext ctx)
    {
        super(ctx);
        ctx.setViewContext(getViewContext());
        _dataRegion = dataRegion;
        setFrame(FrameType.DIV);
    }

    public DataView(DataRegion dataRegion, Errors errors)
    {
        super(FrameType.DIV);
        _model = new RenderContext(getViewContext(), errors);
        _dataRegion = dataRegion;
    }

    @NotNull
    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext3"));
        resources.addAll(super.getClientDependencies());

        return resources;
    }

    public DataView(@Nullable DataRegion dataRegion, TableViewForm form, Errors errors)
    {
        this(dataRegion, errors);
        getRenderContext().setForm(form);
    }

    public DataView(TableViewForm form, Errors errors)
    {
        this(null, form, errors);
    }

    public Results getResults()
    {
        return getRenderContext().getResults();
    }

    @Deprecated
    public ResultSet getResultSet()
    {
        return getRenderContext().getResults();
    }

    public void setResults(Results rs)
    {
        getRenderContext().setResults(rs);
    }

    @Deprecated
    public void setResultSet(ResultSet rs)
    {
        getRenderContext().setResults(new ResultsImpl(rs));
    }

    @Override
    public void setTitle(CharSequence title)
    {
        super.setTitle(title);
        if (StringUtils.isNotEmpty(title) && getFrame()==FrameType.DIV)
            setFrame(FrameType.PORTAL);
    }

    public RenderContext getRenderContext()
    {
        return getModelBean();
    }

    public DataRegion getDataRegion()
    {
        if (null != _dataRegion)
            return _dataRegion;

        TableViewForm form = getRenderContext().getForm();
        if (null != form)
        {
            DataRegion dr = new DataRegion();
            dr.setTable(form.getTable());
            List<ColumnInfo> allCols = form.getTable().getUserEditableColumns();
            List<ColumnInfo> includedCols = new ArrayList<>();
            for (ColumnInfo col : allCols)
            {
                if (isColumnIncluded(col))
                {
                    includedCols.add(col);
                }
            }
            dr.setColumns(includedCols);
            _dataRegion = dr;
        }

        return _dataRegion;
    }

    /** Whether the column should be included at all in the current view */
    protected abstract boolean isColumnIncluded(ColumnInfo col);

    public void setContainer(Container c)
    {
        getRenderContext().setContainer(c);
    }


    @NotNull
    public TableInfo getTable()
    {
        TableInfo t;
        if (null != _dataRegion)
        {
            t = _dataRegion.getTable();
            if (t == null)
            {
                throw new IllegalStateException("Could not get TableInfo from DataRegion " + _dataRegion.getName() + " of type " + _dataRegion.getClass());
            }
        }
        else if (null != getRenderContext().getForm())
        {
            TableViewForm form = getRenderContext().getForm();
            t = form.getTable();
            if (t == null)
            {
                throw new IllegalStateException("Could not get TableInfo from form " + form);
            }
        }
        else
        {
            throw new IllegalStateException("No DataRegion or form to supply TableInfo");
        }

        // see https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=15584
        if (null == t.getSchema())
            throw new NullPointerException("getSchema() returned null: " + t.getName() + " " + t.getClass().getName());
        return t;
    }


    protected abstract void _renderDataRegion(RenderContext ctx, Writer out) throws IOException;


    @Override
    public void renderView(RenderContext model, PrintWriter out) throws IOException
    {
        _renderDataRegion(getRenderContext(), out);
    }

    public String createVerifySelectedScript(ActionURL url, String objectsDescription)
    {
        return "if (verifySelected(" + getDataRegion().getJavascriptFormReference() + ", '" + url.getLocalURIString() + "', 'post', '" + objectsDescription + "')) { " + getDataRegion().getJavascriptFormReference() + ".submit(); }";
    }

    protected boolean verboseErrors()
    {
        return true;
    }

    /** TODO duplicated code in QueryView **/
    protected void renderErrors(PrintWriter writer, String message, List<? extends Throwable> errors)
    {
        StringWriter out = new StringWriter();
        out.write("<p class=\"labkey-error\">");
        out.write(PageFlowUtil.filter(message));
        out.write("</p>");

        Set<String> seen = new HashSet<>();

        if (verboseErrors())
        {
            for (Throwable e : errors)
            {
                String queryName =   ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.QueryName);
                String resolveURL =  ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.ResolveURL);
                String resolveText = ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.ResolveText);

                if (e instanceof QueryParseException)
                {
                    String msg = e.getMessage();
                    if (!StringUtils.isEmpty(queryName) && !msg.contains(queryName))
                        msg += " in query " + queryName;
                    out.write(PageFlowUtil.filter(e.getMessage()));
                }
                else
                {
                    out.write(PageFlowUtil.filter(e.toString()));
                }

                if (null != resolveURL && seen.add(resolveURL))
                {
                    User user = getRenderContext().getViewContext().getUser();
                    if (user.isSiteAdmin() || user.isDeveloper())
                    {
                        out.write("&nbsp;");
                        out.write(PageFlowUtil.textLink(StringUtils.defaultString(resolveText, "resolve"), resolveURL));
                    }
                }
                out.write("<br>");
            }
        }
        writer.write(out.toString());
    }


    /**
     * Since we're using user-defined sql, we can get a SQLException that
     * doesn't indicate a bug in the product. Don't log to mothership,
     * and tell the user what happened
     */
    @Override
    protected void renderException(Throwable t, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        if (t instanceof SQLException || t instanceof QueryParseException)
        {
            renderErrors(response.getWriter(), "View " + " has errors", Collections.singletonList(t));
        }
        else if (t instanceof BadSqlGrammarException || t instanceof DataIntegrityViolationException)
        {
            Throwable cause = t.getCause();
            renderErrors(response.getWriter(), "View " + " has errors", Collections.singletonList(cause));
        }
        else
        {
            super.renderException(t, request, response);
        }
    }
}
