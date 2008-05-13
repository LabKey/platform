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

import org.labkey.api.query.*;

public class DesignForm extends QueryForm
{
    public String ff_designXML;
    public boolean ff_dirty;
    public QueryAction ff_redirect = QueryAction.designQuery;
    public void setFf_designXML(String value)
    {
        ff_designXML = value;
    }
    public void setFf_dirty(boolean value)
    {
        ff_dirty = value;
    }
    public void setFf_redirect(String value)
    {
        ff_redirect = QueryAction.valueOf(value);
    }

    public String getDefaultTab()
    {
        return getViewContext().getRequest().getParameter(QueryParam.defaultTab.toString());
    }

}
