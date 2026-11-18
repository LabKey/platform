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
package org.labkey.api.qc;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.vfs.FileLike;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TsvDataSerializerTestCase extends Assert
{
    @Test
    public void testDateTimeKeepsSeconds() throws Exception
    {
        Date original = Date.from(LocalDateTime.of(2026, 3, 4, 10, 15, 42).atZone(ZoneId.systemDefault()).toInstant());
        Map<String, Object> row = Map.of("Name", "a", "When", original);
        DataIteratorBuilder dib = MapDataIterator.of(Set.of("Name", "When"), List.of(row));
        TSVWriter tsvWriter = new TSVWriter()
        {
            @Override
            protected int write()
            {
                throw new UnsupportedOperationException();
            }
        };

        FileLike runDataFile = FileUtil.createTempFileLike("runData", ".tsv");
        try
        {
            new TsvDataSerializer().exportRunData(null, List.of(dib), runDataFile, tsvWriter);

            List<String> lines = Files.readAllLines(runDataFile.toNioPathForRead(), StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            int col = List.of(lines.get(0).split("\t")).indexOf("When");
            assertTrue("Missing When column: " + lines.get(0), col >= 0);
            String written = lines.get(1).split("\t")[col];
            // Parse without DateUtil.parseDateTime(String), which reads LookAndFeelProperties from the database
            long parsed = DateUtil.parseDateTime(written, DateUtil.MonthDayOption.MONTH_DAY, true, null);
            assertEquals("Written value: " + written, original, new Date(parsed));
        }
        finally
        {
            runDataFile.delete();
        }
    }
}
