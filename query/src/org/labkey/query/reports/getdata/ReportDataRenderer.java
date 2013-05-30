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
        @JsonSubTypes.Type(value=JSONReportDataRenderer.class),
        @JsonSubTypes.Type(value=QueryWebPartDataRenderer.class)})
public interface ReportDataRenderer
{
    public ApiResponse render(QueryReportDataSource source, ViewContext context, Errors errors);
}
