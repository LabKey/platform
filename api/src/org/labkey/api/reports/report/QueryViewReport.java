/*
 * Copyright (c) 2010 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.view.ViewContext;

/*
* User: adam
* Date: Dec 21, 2010
* Time: 7:57:11 PM
*/
public abstract class QueryViewReport extends AbstractReport
{
    /**
     * Create the query view used to generate the result set that this report operates on.
     */
    protected QueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = (UserSchema) DefaultSchema.get(context.getUser(), context.getContainer()).getSchema(schemaName);

            if (base != null)
            {
                QuerySettings settings = base.getSettings(context, dataRegionName);
                settings.setSchemaName(schemaName);
                settings.setQueryName(queryName);
                settings.setViewName(viewName);
                // need to reset the report id since we want to render the data grid, not the report
                settings.setReportId(null);

                UserSchema schema = base.createView(context, settings).getSchema();
                return new ReportQueryView(schema, settings);
            }
        }

        return null;
    }
}
