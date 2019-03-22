/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
package org.labkey.api.study.reports;

import org.labkey.api.reports.report.QueryReportDescriptor;

import java.util.Arrays;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Nov 17, 2006
 */
public class CrosstabReportDescriptor extends QueryReportDescriptor
{
    public static final String TYPE = "crosstabDescriptor";
    public static final String STATS = "stats";

    public CrosstabReportDescriptor()
    {
        setDescriptorType(TYPE);
        setStats(new String[]{"Count"});
        setProperty("statField", "SequenceNum");
    }

    public void setStats(String[] stats){_props.put(STATS, Arrays.asList(stats));}

    public String[] getStats()
    {
        final Object stats = _props.get(STATS);
        if (stats instanceof List)
            return ((List<String>)stats).toArray(new String[0]);
        else if (stats instanceof String)
            return new String[]{(String)stats};

        return new String[]{""};
    }

    public boolean isArrayType(String prop)
    {
        if (!super.isArrayType(prop))
        {
            return STATS.equals(prop);
        }
        return true;
    }
}
