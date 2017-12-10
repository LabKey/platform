/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
/*
 * User: Karl Lum
 * Date: Jul 15, 2008
 * Time: 2:53:11 PM
 */

public interface QueryUrls extends UrlProvider
{
    ActionURL urlSchemaBrowser(Container c);
    ActionURL urlSchemaBrowser(Container c, String schemaName);
    ActionURL urlSchemaBrowser(Container c, String schemaName, String queryName);
    ActionURL urlCreateExcelTemplate(Container c, String schemaName, String queryName);
    /** @param baseURL URL from which to preserve parameters when creating the new URL */
    ActionURL urlStartBackgroundRReport(@NotNull ActionURL baseURL, String reportId);
    /**
     * @param baseURL URL from which to preserve parameters (such as schemaName and queryName) when creating the new URL  */
    ActionURL urlExecuteQuery(@NotNull ActionURL baseURL);
    /**
     * @param c Container
     * @param schema Query schema name
     * @param query Query name
     */
    ActionURL urlExecuteQuery(Container c, String schema, String query);
}
