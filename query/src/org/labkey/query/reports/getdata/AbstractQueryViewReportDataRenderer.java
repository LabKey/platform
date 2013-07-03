/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.query.controllers.QueryController;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * User: jeckels
 * Date: 5/29/13
 */

public abstract class AbstractQueryViewReportDataRenderer implements ReportDataRenderer
{
    private int _offset;
    private boolean _includeDetailsColumn;
    private Integer _maxRows = null;
    private Sort _sort = new Sort();
    private List<FieldKey> _columns;
    @NotNull private String _dataRegionName = "dataregion";

    private void setOffset(int offset)
    {
        _offset = offset;
    }

    private void setMaxRows(int maxRows)
    {
        _maxRows = maxRows;
    }

    private void setIncludeDetailsColumn(boolean includeDetailsColumn)
    {
        _includeDetailsColumn = includeDetailsColumn;
    }

    public void setSort(List<Sort.SortFieldBuilder> sorts)
    {
        for (Sort.SortFieldBuilder sort : sorts)
        {
            _sort.appendSortColumn(sort.create());
        }
    }

    public void setColumns(List<FieldKey> columns)
    {
        _columns = columns;
    }

    public void setDataRegionName(@NotNull String dataRegionName)
    {
        _dataRegionName = dataRegionName;
    }

    @Override
    public ApiResponse render(QueryReportDataSource source, ViewContext context, Errors errors)
    {
        final QueryDefinition queryDefinition = QueryService.get().saveSessionQuery(context, context.getContainer(), source.getSchema().getSchemaName(), source.getLabKeySQL());
    
        QuerySettings settings = new QuerySettings(context, _dataRegionName, queryDefinition.getName())
        {
            @Override
            protected QueryDefinition createQueryDef(UserSchema schema)
            {
                return queryDefinition;
            }
        };
        settings.setSchemaName(source.getSchema().getSchemaName());
        settings.setOffset(_offset);
        settings.setBaseSort(_sort);
        if (_columns != null)
        {
            settings.setFieldKeys(_columns);
        }
    
        settings.setShowRows(ShowRows.PAGINATED);
        if (null == _maxRows)
        {
            settings.setMaxRows(QueryController.DEFAULT_API_MAX_ROWS);
        }
        else
        {
            settings.setMaxRows(_maxRows.intValue());
        }
    
        QueryView view = new QueryView(source.getSchema(), settings, errors);

        return createApiResponse(view);
    }

    public int getOffset()
    {
        return _offset;
    }

    public boolean isIncludeDetailsColumn()
    {
        return _includeDetailsColumn;
    }

    public Integer getMaxRows()
    {
        return _maxRows;
    }

    public Sort getSort()
    {
        return _sort;
    }

    protected abstract ApiResponse createApiResponse(QueryView view);
}
