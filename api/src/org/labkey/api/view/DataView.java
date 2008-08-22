/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.labkey.api.data.*;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;


public abstract class DataView extends WebPartView<RenderContext>
{
    private DataRegion _dataRegion = null;

    private static Logger _log = Logger.getLogger(DataView.class);

    // Call this constructor if you need to subclass the RenderContext
    public DataView(DataRegion dataRegion, RenderContext ctx)
    {
        super(ctx);
        ctx.setViewContext(getViewContext());
        _dataRegion = dataRegion;
    }


    public DataView(DataRegion dataRegion, BindException errors)
    {
        _model = new RenderContext(getViewContext(), errors);
        _dataRegion = dataRegion;
    }

    
    public DataView(DataRegion dataRegion, TableViewForm form, BindException errors)
    {
        this(dataRegion, errors);
        getRenderContext().setForm(form);
    }

    public DataView(TableViewForm form, BindException errors)
    {
        this(null, form, errors);
    }

    public ResultSet getResultSet()
    {
        return getRenderContext().getResultSet();
    }

    public void setResultSet(ResultSet rs)
    {
        getRenderContext().setResultSet(rs);
    }

    public void setDataRegion(DataRegion dataRegion)
    {
        this._dataRegion = dataRegion;
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
            dr.setColumns(form.getTable().getUserEditableColumns());
            _dataRegion = dr;
        }

        return _dataRegion;
    }

    public void setContainer(Container c)
    {
        getRenderContext().setContainer(c);
    }

    public TableInfo getTable()
    {
        if (null != _dataRegion)
            return _dataRegion.getTable();
        else if (null != getRenderContext().getForm())
            return getRenderContext().getForm().getTable();
        else
            return null;
    }

    protected abstract void _renderDataRegion(RenderContext ctx, Writer out) throws IOException, SQLException;


    @Override
    public void renderView(RenderContext model, PrintWriter out) throws IOException, ServletException
    {
        try
        {
            _renderDataRegion(getRenderContext(), out);
        }
        catch (SQLException x)
        {
            _log.log(Level.ERROR, this, x);
        }
    }

    public String createVerifySelectedScript(ActionURL url, String objectsDescription, boolean htmlEncode)
    {
        return "javascript: if (verifySelected(" + getDataRegion().getJavascriptFormReference(htmlEncode) + ", '" + url.getLocalURIString() + "', 'post', '" + objectsDescription + "')) { " + getDataRegion().getJavascriptFormReference(htmlEncode) + ".submit(); }";
    }
}
