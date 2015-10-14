/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.di.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.VBox;
import org.labkey.di.DataIntegrationQuerySchema;

public class ProcessJobsView extends VBox
{
    public ProcessJobsView(User user, Container container)
    {
        DataIntegrationQuerySchema schema = new DataIntegrationQuerySchema(user, container);
        QuerySettings settings = new QuerySettings(getViewContext(), "processJobs", DataIntegrationQuerySchema.TRANSFORMHISTORY_TABLE_NAME);

        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("DateRun"), Sort.SortDirection.DESC);
        QueryView processedJobsGrid = new QueryView(schema, settings, null);
        HtmlView buttons = new HtmlView(
                PageFlowUtil.button("Scheduler").href(DataIntegrationController.BeginAction.class, container).toString()
        );

        addView(processedJobsGrid);
        addView(buttons);
    }
}
