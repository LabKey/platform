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
package org.labkey.study.importer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.iterator.BeanIterator;
import org.labkey.api.iterator.CloseableFilteredIterator;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.Filter;
import org.labkey.api.iterator.IteratorUtil;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitTag;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 8:44:01 PM
 */
public class DataFaxVisitMapReader implements VisitMapReader
{
    private final String _content;

    public DataFaxVisitMapReader(String content)
    {
        _content = content;
    }

    @Override
    @NotNull
    public List<VisitMapRecord> getVisitMapRecords(TimepointType timepointType) throws IOException, VisitMapParseException
    {
        String tsv = _content.replace('|','\t');

        try
        {
            TabLoader loader = new TabLoader(tsv, false);

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

            // Apply a filter to the iterator
            CloseableFilteredIterator<Map<String, Object>> filtered = new CloseableFilteredIterator<>(loader.iterator(), new Filter<Map<String, Object>>()
            {
                public boolean accept(Map<String, Object> map)
                {
                    return null != map.get("sequenceRange");
                }
            });

            // Convert to an iterator of VisitMapRecord beans
            BeanIterator<VisitMapRecord> iterator = new BeanIterator<>(filtered, VisitMapRecord.class);

            return IteratorUtil.toList(iterator);
        }
        catch (NumberFormatException x)
        {
            throw new VisitMapParseException("Error parsing data fax format visit map", x);
        }
    }

    @Override
    @NotNull
    public List<StudyManager.VisitAlias> getVisitImportAliases()
    {
        // This format does not support import aliases
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public List<VisitTag> getVisitTags() throws VisitMapParseException
    {
        // This format does not support visitTags
        return Collections.emptyList();
    }
}
