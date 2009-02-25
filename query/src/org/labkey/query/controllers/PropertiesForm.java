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

import org.labkey.api.query.QueryForm;

public class PropertiesForm extends QueryForm
{
	public String rename;
    public String description;
    public boolean inheritable = false;
    public boolean hidden = false;

	public PropertiesForm()
	{
		super();
	}

	public PropertiesForm(String schemaName, String queryName)
	{
		super(schemaName, queryName);
	}

	public void setRename(String name)
	{
		this.rename = name;
	}
	
    public void setInheritable(boolean b)
    {
        inheritable = b;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public void setHidden(boolean b)
    {
        hidden = b;
    }
}
