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

public class PropertiesForm extends QueryForm
{
    public String ff_description;
    public boolean ff_inheritable = false;
    public boolean ff_hidden = false;

    public void setFf_inheritable(boolean b)
    {
        ff_inheritable = b;
    }

    public void setFf_description(String description)
    {
        ff_description = description;
    }

    public void setFf_hidden(boolean b)
    {
        ff_hidden = b;
    }
}
