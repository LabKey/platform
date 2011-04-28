/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.DemoMode;

/*
* User: adam
* Date: Apr 26, 2011
* Time: 6:44:28 PM
*/
public class PtidObfuscatingDisplayColumn extends DataColumn
{
    public PtidObfuscatingDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public String getValue(RenderContext ctx)
    {
        String value = super.getValue(ctx).toString();
        return DemoMode.obfuscate(value);
    }

    @Override
    public String getFormattedValue(RenderContext ctx)
    {
        return getValue(ctx);
    }
}
