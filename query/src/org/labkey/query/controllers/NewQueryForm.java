/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.query.controllers;

import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryAction;

public class NewQueryForm extends QueryForm
{
    public String ff_newQueryName;
    public String ff_baseTableName;
    public QueryAction ff_redirect = QueryAction.sourceQuery;

    public void setFf_newQueryName(String name)
    {
        ff_newQueryName = name;
    }

    public void setFf_baseTableName(String name)
    {
        ff_baseTableName = name;
    }

    public void setFf_redirect(String redirect)
    {
        if (redirect != null)
            ff_redirect = QueryAction.valueOf(redirect);
    }
}
