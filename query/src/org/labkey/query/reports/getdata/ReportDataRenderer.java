package org.labkey.query.reports.getdata;

import org.labkey.api.action.ApiResponse;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

/**
 * Responsible for creating the actual response that is sent back to the client from a GetData API request.
 *
 * User: jeckels
 * Date: 5/15/13
 */
public interface ReportDataRenderer
{
    public ApiResponse render(QueryReportDataSource source, ViewContext context, Errors errors);
}
