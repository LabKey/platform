/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

import org.labkey.api.data.Container;

import java.util.ArrayList;
import java.util.List;

/*
* User: dave
* Date: Jan 12, 2010
* Time: 2:39:30 PM
*/
public class MoveWorkbooksBean
{
    private List<Container> _workbooks = new ArrayList<>();

    public void addWorkbook(Container workbook)
    {
        _workbooks.add(workbook);
    }

    public List<Container> getWorkbooks()
    {
        return _workbooks;
    }

    public String getIDInitializer()
    {
        StringBuilder ids = new StringBuilder();
        String sep = "";
        for (Container wb : _workbooks)
        {
            ids.append(sep);
            ids.append(wb.getRowId());
            sep = ",";
        }
        return ids.toString();
    }
}
