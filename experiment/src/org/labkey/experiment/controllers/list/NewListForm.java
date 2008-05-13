/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.view.ViewForm;

public class NewListForm extends ViewForm
{
    public String ff_name;
    public String ff_keyType = ListDefinition.KeyType.AutoIncrementInteger.toString();
    public String ff_keyName = "Key";
    public String ff_description;

    public void setFf_name(String ff_name)
    {
        this.ff_name = ff_name;
    }

    public void setFf_keyType(String ff_keyType)
    {
        this.ff_keyType = ff_keyType;
    }

    public void setFf_keyName(String ff_keyName)
    {
        this.ff_keyName = ff_keyName;
    }

    public void setFf_description(String ff_description)
    {
        this.ff_description = ff_description;
    }
}
