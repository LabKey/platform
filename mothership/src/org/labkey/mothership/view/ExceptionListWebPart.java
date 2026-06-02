/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.mothership.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.view.VBox;
import org.labkey.mothership.query.MothershipSchema;
import org.springframework.validation.BindException;

public class ExceptionListWebPart extends VBox
{
    public ExceptionListWebPart(User user, Container container, BindException errors)
    {
        MothershipSchema schema = new MothershipSchema(user, container);
        QuerySettings settings = schema.getSettings(getViewContext(), "ExceptionSummary", MothershipSchema.EXCEPTION_STACK_TRACE_TABLE_NAME);
        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("ExceptionStackTraceId"), Sort.SortDirection.DESC);

        QueryView queryView = schema.createView(getViewContext(), settings, errors);
        queryView.setShowDetailsColumn(false);
        queryView.setShadeAlternatingRows(true);
        queryView.setShowBorders(true);

        addView(new LinkBar());
        addView(queryView);
    }
}
