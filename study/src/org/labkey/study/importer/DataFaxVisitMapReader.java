/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.api.reader.BeanTabLoader;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.util.Filter;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 8:44:01 PM
 */
public class DataFaxVisitMapReader implements VisitMapReader
{
    public List<VisitMapRecord> getRecords(String content, Logger logger)
    {
        String tsv = content.replace('|','\t');

        try
        {
            BeanTabLoader<VisitMapRecord> loader = new BeanTabLoader<VisitMapRecord>(VisitMapRecord.class, tsv, false);
            loader.setColumns(new ColumnDescriptor[]
            {
                new ColumnDescriptor("sequenceRange", String.class),
                new ColumnDescriptor("visitType", String.class),
                new ColumnDescriptor("visitLabel", String.class),
                new ColumnDescriptor("visitDatePlate", Integer.class),
                new ColumnDescriptor("visitDateField", String.class),
                new ColumnDescriptor("visitDueDay", Integer.class),
                new ColumnDescriptor("visitDueAllowance", Integer.class),
                new ColumnDescriptor("requiredPlates", String.class),
                new ColumnDescriptor("optionalPlates", String.class),
                new ColumnDescriptor("missedNotificationPlate", Integer.class),
                new ColumnDescriptor("terminationWindow", String.class)
            });
            loader.setMapFilter(new Filter<Map<String, Object>>()
            {
                public boolean accept(Map<String, Object> map)
                {
                    return null != map.get("sequenceRange");
                }
            });

            return loader.load();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }
}
