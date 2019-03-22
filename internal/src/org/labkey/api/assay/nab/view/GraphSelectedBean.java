/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.assay.nab.view;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * User: klum
 * Date: 6/11/13
 */
public abstract class GraphSelectedBean
{
    protected ViewContext _context;
    protected int[] _cutoffs;
    protected ExpProtocol _protocol;
    protected int[] _dataObjectIds;
    protected QueryView _queryView;
    protected int[] _graphableIds;
    protected String _captionColumn;
    protected String _chartTitle;

    public GraphSelectedBean(ViewContext context, ExpProtocol protocol, int[] cutoffs, int[] dataObjectIds, String captionColumn, String chartTitle)
    {
        _context = context;
        _cutoffs = cutoffs;
        _protocol = protocol;
        _dataObjectIds = dataObjectIds;
        _captionColumn = captionColumn;
        _chartTitle = chartTitle;
    }

    public int[] getCutoffs()
    {
        return _cutoffs;
    }

    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    public String getCaptionColumn()
    {
        return _captionColumn;
    }

    public String getChartTitle()
    {
        return _chartTitle;
    }

    public int[] getGraphableObjectIds() throws IOException, SQLException
    {
        if (_graphableIds == null)
        {
            QueryView dataView = getQueryView();

            try (ResultSet rs = dataView.getResultSet())
            {
                Set<Integer> graphableIds = new HashSet<>();
                while (rs.next())
                    graphableIds.add(rs.getInt("RowId"));
                _graphableIds = new int[graphableIds.size()];
                int i = 0;
                for (Integer id : graphableIds)
                    _graphableIds[i++] = id;
            }
        }
        return _graphableIds;
    }

    public QueryView getQueryView()
    {
        if (_queryView == null)
        {
            _queryView = createQueryView();
        }
        return _queryView;
    }

    protected abstract QueryView createQueryView();

    public abstract ActionURL getGraphRenderURL();
}
