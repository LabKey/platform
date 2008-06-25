/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.study.dataset.client;

import java.util.*;

/*
* User: jgarms
* Date: May 20, 2008
* Time: 4:30:33 PM
*/
public class GWTTabLoader
{
    private final String tsv;

    public GWTTabLoader(String tsv)
    {
        this.tsv = tsv;
    }

    public Map[] getData()
    {
        List rows = getRows();

        if (rows.size() < 2)
        {
            return new Map[0];
        }

        // First row is keys -- lowercase for easier access
        String[] keys = ((String)rows.get(0)).split("\t");
        for (int i=0;i<keys.length;i++)
        {
            keys[i] = keys[i].trim().toLowerCase();
        }

        Map[] data = new Map[rows.size() - 1];

        // Iterate starting at 1, since our keys were the first row
        for (int i=1;i<rows.size(); i++)
        {
            String row = (String)rows.get(i);
            String[] rowData = row.split("\t");
            Map map = new HashMap();
            for (int j=0;j<keys.length;j++)
            {
                String fieldData = "";
                if (j < rowData.length)
                    fieldData = rowData[j];
                map.put(keys[j], fieldData);
            }

            data[i - 1] = map;
        }
        return data;

    }

    private List/*<String>*/ getRows()
    {
        List rows = new ArrayList();
        StringBuffer sb = new StringBuffer();

        for (int index = 0; index < tsv.length(); index++)
        {
            char c = tsv.charAt(index);
            if (c == '\n' || c == '\r')
            {
                if (sb.length() == 0)
                {
                    // Handle windows \r\n
                    continue;
                }
                rows.add(sb.toString());
                sb.setLength(0);
            }
            else
            {
                sb.append(c);
            }
        }
        // Handle last row which may not be properly terminated
        if (sb.length() > 0)
            rows.add(sb.toString());
        return rows;
    }

}
