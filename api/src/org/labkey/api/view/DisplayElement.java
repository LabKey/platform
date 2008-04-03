/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.codehaus.groovy.control.CompilationFailedException;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.ACL;
import org.labkey.api.util.BooleanExpression;
import org.labkey.api.util.GroovyExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.MemTracker;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

public abstract class DisplayElement implements View, Cloneable
{
    private int _displayPermission = ACL.PERM_READ;
    private BooleanExpression _visible = new BooleanExpression(true);
    private int _displayModes = DataRegion.MODE_ALL;
    protected StringExpressionFactory.StringExpression _caption = null;
    protected boolean _locked = false;

    public DisplayElement()
    {
        assert MemTracker.put(this);
    }

    public String getContentType()
    {
        return "text/html";
    }

    public int getDisplayPermission()
    {
        return _displayPermission;
    }

    public void setDisplayPermission(int displayPermission)
    {
        checkLocked();
        _displayPermission = displayPermission;
    }

    public boolean getVisible(Map ctx)
    {
        return _visible.get(ctx);
    }


    public void setVisible(boolean visible)
    {
        checkLocked();
        _visible.set(visible);
    }


    public void setVisibleExpr(Object visible) throws IOException, CompilationFailedException
    {
        checkLocked();
        if (null == visible)
            throw new IllegalArgumentException();
        if (visible instanceof Boolean)
            _visible.set((Boolean)visible);
        else if (visible instanceof String)
           _visible.set(new GroovyExpression((String)visible));
        else
            throw new IllegalArgumentException();
    }


    public Object getVisibleExpr()
    {
        if (null != _visible.getStringExpr())
            return _visible.getStringExpr();
        else
            return _visible.getBooleanExpr();
    }


    public boolean shouldRender(RenderContext ctx)
    {
        return _visible.get(ctx) && ctx.getViewContext().hasPermission(_displayPermission);
    }

    public String getOutput(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            render(ctx, writer);
        }
        catch (IOException e)
        {
            writer.write(e.getMessage());
        }

        return writer.toString();
    }


    public String getCaption(RenderContext ctx)
    {
        return null == _caption ? null : _caption.eval(ctx);
    }


    public String getCaption()
    {
        return null == _caption ? null : _caption.getSource();
    }


    public void setCaption(String caption)
    {
        checkLocked();
        _caption = StringExpressionFactory.create(caption);
        assert _caption.toString().equals(caption);
    }


    public String getCaptionExpr()
    {
        return _caption == null ? null : _caption.toString();
    }


    public int getDisplayModes()
    {
        return _displayModes;
    }

    public void setDisplayModes(int displayModes)
    {
        checkLocked();
        _displayModes = displayModes;
    }

    /**
     * convert org.springframework.web.servlet.View.render(Map) to DisplayElement.render(ModelBean)
     */
    public final void render(Map map, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        assert map instanceof RenderContext;
        render((RenderContext)map,  request, response);
    }

    
    /**
     * org.springframework.framework.web.View
     */
    public void render(RenderContext ctx, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        render(ctx, response.getWriter());
    }


    // This is not WebPartView.renderView() */
    public void renderView(Map model, Writer out) throws IOException
    {
        RenderContext ctx;
        if (model instanceof RenderContext)
            ctx = (RenderContext) model;
        else if (model instanceof ViewContext)
            ctx = new RenderContext((ViewContext)model);
        else
            throw new IllegalStateException();

        render(ctx, out);
    }


    public abstract void render(RenderContext ctx, Writer out) throws IOException;


    public void lock()
    {
        _locked = true;
    }

    protected void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("Object has already been locked");
    }
}
