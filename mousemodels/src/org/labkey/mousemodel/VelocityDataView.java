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
package org.labkey.mousemodel;

import org.apache.velocity.VelocityContext;
import org.labkey.api.data.*;

import javax.servlet.ServletException;
import java.sql.ResultSet;
import java.sql.SQLException;


public class VelocityDataView extends VelocityView
{
    private DataRegion _dataRegion = null;
    private RenderContext _rc;


    public VelocityDataView(DataRegion dataRegion, String templateName)
    {
        this(dataRegion, null, templateName);
    }


    public VelocityDataView(DataRegion dataRegion, TableViewForm form, String templateName)
    {
        super(templateName);
        _rc = new RenderContext(getViewContext());
        if (null != form)
            _rc.setForm(form);
        _dataRegion = dataRegion;
    }


    public VelocityDataView(TableViewForm form, String templateName)
    {
        this(null, form, templateName);
    }


    public int getMode()
    {
        return _rc.getMode();
    }

    public void setMode(int mode)
    {
        _rc.setMode(mode);
    }

    public void setForm(TableViewForm form)
    {
        _rc.setForm(form);
    }

    public TableViewForm getForm()
    {
        return _rc.getForm();
    }

    public ResultSet getResultSet()
    {
        return _rc.getResultSet();
    }

    public void setResultSet(ResultSet rs)
    {
        _rc.setResultSet(rs);
    }

    public void setDataRegion(DataRegion dataRegion)
    {
        this._dataRegion = dataRegion;
    }

    public DataRegion getDataRegion()
    {
        if (null != _dataRegion)
            return _dataRegion;

        if (null != _rc.getForm())
        {
            DataRegion dr = new DataRegion();
            dr.setColumns(_rc.getForm().getTable().getUserEditableColumns());
            _dataRegion = dr;
        }

        return _dataRegion;
    }

    public void setContainer(Container c)
    {
        _rc.setContainer(c);
    }

    public TableInfo getTable()
    {
        if (null != _dataRegion)
            return _dataRegion.getTable();
        else if (null != _rc.getForm())
            return _rc.getForm().getTable();
        else
            return null;
    }

    @Override
    protected void prepareWebPart(Object model) throws ServletException
    {
        VelocityContext requestContext = getRequestContext(getViewContext());

        //Make sure we have enough data for update. getResultSet 
        //is for grid.
        if (_rc.getMode() == DataRegion.MODE_UPDATE && !_rc.getForm().isDataLoaded())
            try
            {
                _rc.getForm().refreshFromDb(false);
            }
            catch (SQLException x)
            {
                throw new ServletException(x);
            }
            //If in details mode, form implies filtering...
        else if (_rc.getMode() == DataRegion.MODE_DETAILS && _rc.getBaseFilter() == null && _rc.getForm() != null)
            _rc.setBaseFilter(new PkFilter(getDataRegion().getTable(), _rc.getForm().getPkVals(), true));
        BoundDataRegion bdr = new BoundDataRegion(getDataRegion(), _rc);
        requestContext.put("dr", bdr);
    }
}
