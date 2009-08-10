/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

public class InternalNewViewForm
{
    public String ff_schemaName;
    public String ff_queryName;
    public String ff_viewName;
    public boolean ff_share;
    public boolean ff_inherit;

    public void setFf_schemaName(String name)
    {
        ff_schemaName = name;
    }

    public void setFf_queryName(String name)
    {
        ff_queryName = name;
    }

    public void setFf_viewName(String name)
    {
        ff_viewName = name;
    }

    public void setFf_share(boolean share)
    {
        ff_share = share;
    }

    public void setFf_inherit(boolean inherit)
    {
        ff_inherit = inherit;
    }
}
