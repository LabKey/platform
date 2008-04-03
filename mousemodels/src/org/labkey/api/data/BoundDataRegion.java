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

package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.DefaultModelAndView;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

/**

 */
public class BoundDataRegion extends DefaultModelAndView<RenderContext>
{
    static Logger _log = Logger.getLogger(BoundDataRegion.class);
    private DataRegion _rgn;
    private RenderContext _ctx;
    private Map<String, BoundDisplayColumn> _fields; //Bound display columns
    private Iterator _it;
    private BoundRow _boundRow;


    public BoundDataRegion(DataRegion rgn, RenderContext ctx)
    {
        super(rgn, ctx);
        _rgn = rgn;
        _ctx = ctx;
    }

    public class BoundRow
    {
        public Map getFields()
        {
            return BoundDataRegion.this.getFields();
        }
    }

    public class OneRowIterator implements Iterator
    {
        private boolean beforeFirst = true;

        public boolean hasNext()
        {
            return beforeFirst;
        }

        public Object next()
        {
            beforeFirst = false;
            return new BoundRow();
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    public class BoundRowIterator implements Iterator
    {
        private Map<String,Object> _row;

        public boolean hasNext()
        {
            ResultSet rs = _ctx.getResultSet();
            if (null == rs)
            {
                try
                {
                    rs = _rgn.getResultSet(_ctx);
                    _ctx.setResultSet(rs);
                }
                catch (Exception x)
                {
                    _log.error("DataRegion.getResultSet", x);
                    return false;
                }
            }
            try
            {
                return !rs.isLast();
            }
            catch (SQLException e)
            {
                _log.error("isLast", e);
                return true;
            }
        }

        public Object next()
        {
            ResultSet rs = _ctx.getResultSet();
            try
            {
                rs.next();
                if (rs instanceof Table.TableResultSet)
                    _row = ((Table.TableResultSet)rs).getRowMap();
                else
                    _row = ResultSetUtil.mapRow(rs, _row);
                //First time through store the row in velocity
                if (null == _row)
                    _ctx.setRow(_row);

                // There's really only one of these
                if (null == _boundRow)
                    _boundRow = new BoundRow();

                return _boundRow;
            }

            catch (SQLException e)
            {
                _log.error("RenderContext.next()", e);
                return null;
            }
        }

        public void remove()
        {
            throw new UnsupportedOperationException("Can't remove row when iterating");
        }

    }

    public Iterator getRows()
    {
        if (null == _it)
            if (_ctx.getMode() == DataRegion.MODE_INSERT || _ctx.getMode() == DataRegion.MODE_UPDATE)
                _it = new OneRowIterator();
            else
                _it = new BoundRowIterator();

        return _it;
    }

    public Map<String, BoundDisplayColumn> getFields()
    {
        if (null == _fields)
        {
            Map<String, BoundDisplayColumn> fields = new CaseInsensitiveHashMap<BoundDisplayColumn>();
            DisplayColumn[] cols = _rgn.getDisplayColumnArray();
            for (int i = 0; i < cols.length; i++)
                fields.put(cols[i].getName(), new BoundDisplayColumn(cols[i], _ctx, _rgn));

            _fields = fields;
        }

        return _fields;
    }

    public boolean getShowRecordSelectors()
    {
        return _rgn.getShowRecordSelectors();
    }


    public boolean getShowFilters()
    {
        return _rgn.getShowFilters();
    }

    public String getButtonBar() throws IOException
    {
        StringWriter out = new StringWriter();
        ButtonBar bb = _rgn.getButtonBar(_ctx.getMode());
        bb.render(_ctx, out);
        return out.toString();
    }

    public boolean getFixedWidthColumns()
    {
        return _rgn.getFixedWidthColumns();
    }

    public int getMaxRows()
    {
        return _rgn.getMaxRows();
    }

    public String getName()
    {
        return _rgn.getName();
    }

    public String getGrid() throws SQLException, IOException
    {
        StringWriter stringWriter = new StringWriter();
        _rgn.renderTable(_ctx, stringWriter);

        return stringWriter.toString();
    }

    /**
     * Returns a string including the table tag, and col tags.
     * Does not include column headers.
     */
    public String getGridStart() throws IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderGridStart(_ctx, out, _rgn.getDisplayColumnArray());
        return out.toString();
    }

    public String getGridHeaders() throws SQLException, IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderGridHeaders(_ctx, out, _rgn.getDisplayColumnArray());
        return out.toString();
    }

    public String getGridEnd() throws IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderGridEnd(_ctx, out);
        return out.toString();
    }

    protected void renderGridEnd(Writer out) throws IOException
    {
        out.write("</table>\n");
    }

    public String getFormEnd() throws IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderFormEnd(_ctx, out);
        return out.toString();
    }

    public String getTableContents() throws SQLException, IOException
    {
        StringWriter out = new StringWriter();
        ResultSet rs = _ctx.getResultSet();
        if (rs == null)
        {
            rs = _rgn.getResultSet(_ctx);
            _ctx.setResultSet(rs);
        }
        _rgn.renderTableContents(_ctx, out, _rgn.getDisplayColumnArray());
        return out.toString();
    }

    public String getTableRow() throws SQLException, IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderTableRow(_ctx, out, _rgn.getDisplayColumnArray(), 0);
        return out.toString();

    }

    public String getFormHeader() throws IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderFormHeader(out, _ctx.getMode());
        return out.toString();

    }

    public String getRecordSelector() throws IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderRecordSelector(_ctx, out);
        return out.toString();
    }


    public String getDetails() throws SQLException, IOException
    {
        StringWriter out = new StringWriter();
        ResultSet rs = _ctx.getResultSet();
        if (rs == null)
        {
            rs = _rgn.getResultSet(_ctx);
            _ctx.setResultSet(rs);
        }
        _rgn.renderDetails(_ctx, out);
        return out.toString();

    }


    public String getInputForm() throws SQLException, IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderInputForm(_ctx, out);
        return out.toString();
    }


    public String getUpdateForm() throws SQLException, IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderUpdateForm(_ctx, out);
        return out.toString();

    }

    public String getMainErrors() throws IOException
    {
        StringWriter out = new StringWriter();
        _rgn.renderMainErrors(_ctx, out);
        return out.toString();
    }


    public String getHiddenFields() throws IOException
    {
        if (_ctx.getMode() == DataRegion.MODE_UPDATE)
        {
            Object oldValues;
            TableViewForm form = _ctx.getForm();
            oldValues = form.getOldValues();
            if (null == oldValues)
                oldValues = form.getTypedValues();

            StringWriter out = new StringWriter();
            _rgn.renderOldValues(out, oldValues);
            return out.toString();
        }
        else if (_ctx.getMode() == DataRegion.MODE_DETAILS)
        {
            StringWriter out = new StringWriter();
            _rgn.renderDetailsHiddenFields(out, _ctx.getRow());
            return out.toString();
        }
        else
            return "";
    }

    public String getFilterHtml() throws IOException
    {
        StringWriter out = new StringWriter();
        _rgn.writeFilterHtml(_ctx, out);
        return out.toString();

    }

    public String linkButton(String caption, String url) throws IOException
    {
        ActionButton linkButton = new ActionButton(caption);
        linkButton.setActionType(ActionButton.Action.LINK);
        linkButton.setURL(url);
        return buttonString(linkButton);
    }

    public String postButton(String caption, String action) throws IOException
    {
        ActionButton postButton = new ActionButton(action, caption);
        postButton.setActionType(ActionButton.Action.POST);
        return buttonString(postButton);
    }

    public String getButton(String caption, String action) throws IOException
    {
        ActionButton getButton = new ActionButton(action, caption);
        getButton.setActionType(ActionButton.Action.GET);
        return buttonString(getButton);
    }

    public String scriptButton(String caption, String action) throws IOException
    {
        ActionButton getButton = new ActionButton(action, caption);
        getButton.setActionType(ActionButton.Action.GET);
        getButton.setScript(action);
        return buttonString(getButton);
    }

    private String buttonString(ActionButton button) throws IOException
    {
        StringWriter out = new StringWriter();
        button.render(_ctx, out);
        return out.toString();
    }

}
