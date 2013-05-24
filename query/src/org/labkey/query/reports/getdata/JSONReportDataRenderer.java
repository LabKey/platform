package org.labkey.query.reports.getdata;

import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.ReportingApiQueryResponse;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

/**
 * Writes a JSON response containing the column metadata and actual data rows back to the client
 * from a GetData API request.
 *
 * User: jeckels
 * Date: 5/21/13
 */
public class JSONReportDataRenderer implements ReportDataRenderer
{
    public ApiResponse render(QueryReportDataSource source, ViewContext context, Errors errors)
    {
        final QueryDefinition queryDefinition = QueryService.get().saveSessionQuery(context, context.getContainer(), source.getSchema().getSchemaName(), source.getLabKeySQL());

        QueryView view = new QueryView(source.getSchema(), new QuerySettings(context, "dataregion"), errors)
        {
            @Override
            public QueryDefinition getQueryDef()
            {
                return queryDefinition;
            }
        };

        return new ReportingApiQueryResponse(view, context, false, true,
                source.getSchema().getSchemaPath(), queryDefinition.getName(), 0, null,
                false, false, false);
    }
}
