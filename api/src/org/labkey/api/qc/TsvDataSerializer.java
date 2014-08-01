/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

package org.labkey.api.qc;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.DateUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Nov 17, 2009
 */
public class TsvDataSerializer implements DataExchangeHandler.DataSerializer
{
    public void exportRunData(ExpProtocol protocol, List<Map<String, Object>> data, File runDataFile) throws Exception
    {
        if (data.size() > 0)
        {
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runDataFile))))
            {
                // write the column header
                List<String> columns = new ArrayList<>(data.get(0).keySet());
                String sep = "";
                for (String name : columns)
                {
                    pw.append(sep);
                    pw.append(name);
                    sep = "\t";
                }
                pw.println();

                // write the rows
                for (Map<String, Object> row : data)
                {
                    sep = "";
                    for (String name : columns)
                    {
                        Object o = row.get(name);
                        pw.append(sep);
                        if (o != null)
                        {
                            if (Date.class.isAssignableFrom(o.getClass()))
                                pw.append(DateUtil.formatDateTimeISO8601((Date) o));  // Always ISO? Or should we apply display format?
                            else if (MvFieldWrapper.class.isAssignableFrom(o.getClass()))
                                pw.append(String.valueOf(((MvFieldWrapper)o).getOriginalValue()));
                            else if (Collection.class.isAssignableFrom(o.getClass()))
                                pw.append(StringUtils.join((Collection) o, ","));
                            else if (Object[].class.isAssignableFrom(o.getClass()))
                                pw.append(StringUtils.join((Object[]) o, ","));
                            else
                                pw.append(String.valueOf(o));
                        }
                        sep = "\t";
                    }
                    pw.println();
                }
            }
        }
    }

    public List<Map<String, Object>> importRunData(ExpProtocol protocol, File runData) throws Exception
    {
        TabLoader loader = new TabLoader(runData, true);
        loader.setInferTypes(false);
        loader.setHasColumnHeaders(true);
        return loader.load();
    }
}
