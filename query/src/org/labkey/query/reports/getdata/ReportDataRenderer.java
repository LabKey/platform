package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

/**
 * Responsible for creating the actual response that is sent back to the client from a GetData API request.
 *
 * User: jeckels
 * Date: 5/15/13
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, defaultImpl = JSONReportDataRenderer.class, property="type")
@JsonSubTypes({
        @JsonSubTypes.Type(value=JSONReportDataRenderer.class)})
public interface ReportDataRenderer
{
    public ApiResponse render(QueryReportDataSource source, ViewContext context, Errors errors);
}
