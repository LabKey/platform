package org.labkey.study.importer;

import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.api.util.Filter;

import java.util.List;
import java.util.Map;
import java.io.IOException;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 8:44:01 PM
 */
public class DataFaxVisitMapReader implements VisitMapReader
{
    public List<VisitMapRecord> getRecords(String content)
    {
        String tsv = content.replace('|','\t');

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
            loader.setMapFilter(new Filter<Map<String, Object>>()
            {
                public boolean accept(Map<String, Object> map)
                {
                    return null != map.get("sequenceRange");
                }
            });

            return loader.load(VisitMapRecord.class);
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
