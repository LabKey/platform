/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import org.labkey.api.query.AbstractNestableDataRegion;
import org.labkey.api.query.QuerySettings;

import java.io.Writer;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.List;

/**
 * User: jeckels
 */
public class NestableDataRegion extends AbstractNestableDataRegion
{
    private final List<DisplayColumn> _allColumns;

    public NestableDataRegion(List<DisplayColumn> allColumns, String groupingColumnName, String url)
    {
        super(groupingColumnName, url);
        _allColumns = allColumns;
    }

    @Override
    protected List<DisplayColumn> getColumnsForMetadata()
    {
        return _allColumns;
    }

    public Results getResultSet(RenderContext ctx, boolean async) throws SQLException, IOException
    {
        List<DisplayColumn> realColumns = getDisplayColumns();
        setDisplayColumns(_allColumns);
        ctx.setCache(false);
        ResultSet rs = super.getResultSet(ctx, async);
        setDisplayColumns(realColumns);

        _groupedRS = new GroupedResultSet(rs, _uniqueColumnName, getMaxRows());
        _nestedFieldMap = ctx.getFieldMap();
        ctx.setResults(new ResultsImpl(_groupedRS, _nestedFieldMap));
        return ctx.getResults();
    }

    @Override
    protected void renderTableRow(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int rowIndex) throws SQLException, IOException
    {
        super.renderTableRow(ctx, out, showRecordSelectors, renderers, rowIndex);

        _groupedRS.previous();
        ResultSet nestedRS = _groupedRS.getNextResultSet();

        // Validate that the inner and outer result sets are sorted the same
        while (nestedRS.next())
        {
            if (!ctx.getRow().get(_uniqueColumnName).equals(nestedRS.getInt(_uniqueColumnName)))
            {
                throw new IllegalArgumentException("Ids do not match for the outer and inner result sets");
            }
        }
        nestedRS.beforeFirst();

        renderNestedGrid(out, ctx, new ResultsImpl(nestedRS), rowIndex);
        nestedRS.close();
    }

    public void setSettings(QuerySettings settings)
    {
        super.setSettings(settings);
        if (_nestedRegion != null)
            _nestedRegion.setSettings(settings);
    }

    public DataRegion getNestedRegion()
    {
        return _nestedRegion;
    }
}
