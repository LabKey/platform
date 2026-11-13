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
package org.labkey.api.dataiterator;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.query.RuntimeBatchValidationException;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UnexpectedException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Streams over a DataIterator must fail on an invalid row, not silently end there. */
public class DataIteratorStreamTestCase extends Assert
{
    private static final String CSV = "a,b\n1,x\n2,y\n3,z\n";
    private static final int ROW_COUNT = 3;

    private static DataIteratorBuilder data()
    {
        return _ -> {
            try
            {
                DataLoader dl = new TabLoader.CsvFactory().createLoader(IOUtils.toInputStream(CSV, StringUtilsLabKey.DEFAULT_CHARSET), true);
                return dl.getDataIterator(new DataIteratorContext());
            }
            catch (IOException x)
            {
                throw UnexpectedException.wrap(x);
            }
        };
    }

    private static DataIteratorBuilder failOn(String invalidA)
    {
        return DataIteratorUtil.mapTransformer(data(), null, row -> {
            if (invalidA.equals(row.get("a")))
                throw new RuntimeValidationException("invalid row a=" + invalidA);
            return row;
        });
    }

    private static void assertStreamFailsOnInvalidRow(String invalidA)
    {
        DataIteratorContext context = new DataIteratorContext();
        List<Map<String, Object>> rows;
        try
        {
            rows = failOn(invalidA).getDataIterator(context).stream().toList();
        }
        catch (RuntimeBatchValidationException e)
        {
            assertSame("Stream should fail with the context's errors", context.getErrors(), e.getErrors());
            assertEquals("invalid row a=" + invalidA, e.getErrors().getLastRowError().getMessage());
            return;
        }
        fail("Expected stream to fail on invalid row a=" + invalidA + " but stream silently returned " + rows.size() + " of " + ROW_COUNT + " rows");
    }

    @Test
    public void testInvalidMiddleRowFailsStream()
    {
        assertStreamFailsOnInvalidRow("2");
    }

    @Test
    public void testInvalidFirstRowFailsStream()
    {
        assertStreamFailsOnInvalidRow("1");
    }

    @Test
    public void testInvalidRowRecordedInContextWhenNotFailFast()
    {
        DataIteratorContext context = new DataIteratorContext();
        context.setFailFast(false);
        List<Map<String, Object>> rows = failOn("2").getDataIterator(context).stream().toList();
        assertEquals(List.of("1", "3"), rows.stream().map(row -> row.get("a")).toList());
        assertEquals(1, context.getErrors().getRowErrors().size());
        assertEquals(2, context.getErrors().getLastRowError().getRowNumber());
    }
}
