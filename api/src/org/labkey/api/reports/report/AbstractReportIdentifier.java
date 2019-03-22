/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.apache.commons.lang3.math.NumberUtils;

/*
* User: Dave
* Date: Dec 11, 2008
* Time: 3:41:12 PM
*/
public abstract class AbstractReportIdentifier implements ReportIdentifier
{
    public static ReportIdentifier fromString(String id)
    {
        if (null == id || id.length() == 0)
            return null;

        try {return new DbReportIdentifier(id);}
        catch(IllegalArgumentException ignore) {}

        try {return new ModuleReportIdentifier(id);}
        catch(IllegalArgumentException ignore) {}

        // final try to support previous versions of identifiers
        if (NumberUtils.isDigits(id))
        {
            try {return new DbReportIdentifier(NumberUtils.toInt(id));}
            catch(IllegalArgumentException ignore) {}
        }
        return null;
    }

}
