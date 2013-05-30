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
public class JSONReportDataRenderer extends AbstractQueryViewReportDataRenderer
{
    @Override
    protected ApiResponse createApiResponse(QueryView view)
    {
        return new ReportingApiQueryResponse(view, false, true, view.getQueryDef().getName(), getOffset(), null,
                false, isIncludeDetailsColumn(), false);
    }
}
