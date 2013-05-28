package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ReportingApiQueryResponse;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.query.controllers.QueryController;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * Writes a JSON response containing the column metadata and actual data rows back to the client
 * from a GetData API request.
 *
 * User: jeckels
 * Date: 5/21/13
 */
@JsonTypeName("json")
public class JSONReportDataRenderer implements ReportDataRenderer
{
    private int _offset;
    private boolean _includeDetailsColumn;
    private Integer _maxRows = null;
    private Sort _sort = new Sort();

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

    public ApiResponse render(QueryReportDataSource source, ViewContext context, Errors errors)
    {
        final QueryDefinition queryDefinition = QueryService.get().saveSessionQuery(context, context.getContainer(), source.getSchema().getSchemaName(), source.getLabKeySQL());

        QuerySettings settings = new QuerySettings(context, "dataregion");
        settings.setOffset(_offset);
        settings.setBaseSort(_sort);

        settings.setShowRows(ShowRows.PAGINATED);
        if (null == _maxRows)
        {
            settings.setMaxRows(QueryController.DEFAULT_API_MAX_ROWS);
        }
        else
        {
            settings.setMaxRows(_maxRows.intValue());
        }

        QueryView view = new QueryView(source.getSchema(), settings, errors)
        {
            @Override
            public QueryDefinition getQueryDef()
            {
                return queryDefinition;
            }
        };

        return new ReportingApiQueryResponse(view, context, false, true,
                queryDefinition.getName(), _offset, null,
                false, _includeDetailsColumn, false);
    }
}
