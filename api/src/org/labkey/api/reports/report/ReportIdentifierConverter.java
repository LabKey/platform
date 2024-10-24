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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.reports.ReportService;

/*
* User: Dave
* Date: Dec 15, 2008
* Time: 1:53:34 PM
*/
public class ReportIdentifierConverter implements Converter
{
    @Override
    public Object convert(Class type, Object value)
    {
        if (null == value || value.equals("null") || !type.equals(ReportIdentifier.class))
            return null;
        String s = value.toString();
        if (StringUtils.isBlank(s))
            return null;
        var ret =  ReportService.get().getReportIdentifier(s, null, null);
        if (null == ret)
            throw new ConversionException("This does not look like a report identifier");
        return ret;
    }
}
