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
package org.labkey.core.workbook;

import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.data.Container;

/*
* User: dave
* Date: Jan 7, 2010
* Time: 2:28:28 PM
*/

/**
 * Base-class web part factory for web parts that should be available
 * only in Workbook containers
 */
public abstract class WorkbookWebPartFactory extends BaseWebPartFactory
{
    public WorkbookWebPartFactory(String name)
    {
        super(name);
    }

    public WorkbookWebPartFactory(String name, String defaultLocation)
    {
        super(name, defaultLocation);
    }

    protected WorkbookWebPartFactory(String name, String defaultLocation, boolean isEditable, boolean showCustomizeOnInsert)
    {
        super(name, defaultLocation, isEditable, showCustomizeOnInsert);
    }

    @Override
    public boolean isAvailable(Container c, String location)
    {
        return c.isWorkbook();
    }
}
