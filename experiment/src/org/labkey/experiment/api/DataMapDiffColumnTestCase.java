/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.audit.data.DataMapDiffColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataMapDiffColumnTestCase extends Assert
{
    private record Change(@Nullable Map<String, String> oldValues, @Nullable Map<String, String> newValues, String expectedOld, String expectedNew, String expectedChanges) {}

    private static Map<String, String> map(String... keysAndValues)
    {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2)
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        return map;
    }

    @BeforeClass
    public static void setUp()
    {
        JunitUtil.deleteTestContainer();
    }

    @AfterClass
    public static void tearDown()
    {
        JunitUtil.deleteTestContainer();
    }

    @Test
    public void testTsvExport() throws Exception
    {
        Container c = JunitUtil.getTestContainer();
        User user = TestContext.get().getUser();
        String comment = DataMapDiffColumn.class.getName() + " " + GUID.makeGUID();

        List<Change> changes = new ArrayList<>(List.of(
            new Change(null, map("Name", "S-1", "Note", "a&b=c+d%"),
                "", "Name: S-1\nNote: a&b=c+d%", "Name:  > S-1\nNote:  > a&b=c+d%"),
            new Change(map("Name", "S-1", "Amount", "5", "Note", "a&b=c+d%"), map("Name", "S-1", "Amount", "7", "Note", "a&b=c+d%"),
                "Name: S-1\nAmount: 5\nNote: a&b=c+d%", "Name: S-1\nAmount: 7\nNote: a&b=c+d%", "Amount: 5 > 7"),
            new Change(map("Name", "S-1", "Status", "Pending"), map("Name", "S-1", "Units", "\u00b5L"),
                "Name: S-1\nStatus: Pending", "Name: S-1\nUnits: \u00b5L", "Status: Pending > \nUnits:  > \u00b5L"),
            new Change(map("Name", "S-1"), map("Name", "S-1"),
                "Name: S-1", "Name: S-1", ""),
            new Change(map("Name", "S-1"), null,
                "Name: S-1", "", "Name: S-1 > ")
        ));
        // Each row's new map is the next row's old map, and there are more distinct maps than DataMapColumn.decode() caches
        for (int i = 0; i < 10; i++)
            changes.add(new Change(map("Count", String.valueOf(i)), map("Count", String.valueOf(i + 1)),
                "Count: " + i, "Count: " + (i + 1), "Count: " + i + " > " + (i + 1)));

        for (Change change : changes)
        {
            SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(c, comment);
            if (change.oldValues() != null)
                event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(change.oldValues()));
            if (change.newValues() != null)
                event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(change.newValues()));
            AuditLogService.get().addEvent(user, event);
        }

        TableInfo table = AuditLogService.getAuditLogSchema(user, c).getTableOrThrow(SampleTimelineAuditEvent.EVENT_TYPE);
        List<FieldKey> exported = List.of(FieldKey.fromParts("OldValues"), FieldKey.fromParts("NewValues"), FieldKey.fromParts(AbstractAuditTypeProvider.COLUMN_NAME_DATA_CHANGES));
        Map<FieldKey, ColumnInfo> exportedColumns = QueryService.get().getColumns(table, exported);
        List<DisplayColumn> displayColumns = exported.stream().map(fk -> exportedColumns.get(fk).getRenderer()).toList();
        Set<FieldKey> required = new LinkedHashSet<>(exported);
        displayColumns.forEach(dc -> dc.addQueryFieldKeys(required));
        Collection<ColumnInfo> selectColumns = QueryService.get().getColumns(table, required).values();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Comment"), comment);

        StringBuilder actual = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        try (TSVGridWriter writer = new TSVGridWriter(() -> QueryService.get().getSelectBuilder(table).columns(selectColumns).filter(filter).sort(new Sort("RowId")).select(false), displayColumns))
        {
            writer.setHeaderRowVisible(false);
            writer.write(actual);
            for (Change change : changes)
                expected.append(writer.quoteValue(change.expectedOld())).append('\t')
                    .append(writer.quoteValue(change.expectedNew())).append('\t')
                    .append(writer.quoteValue(change.expectedChanges())).append('\n');
        }
        assertEquals(expected.toString(), actual.toString());
    }
}
