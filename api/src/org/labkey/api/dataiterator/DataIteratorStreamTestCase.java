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
import org.labkey.api.query.BatchValidationException;
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

    private static void assertStreamFailsOnInvalidRow(String invalidA)
    {
        String message = "invalid row a=" + invalidA;
        DataIteratorBuilder tx = DataIteratorUtil.mapTransformer(data(), null, row -> {
            if (invalidA.equals(row.get("a")))
                throw new RuntimeValidationException(message);
            return row;
        });

        List<Map<String, Object>> rows;
        try
        {
            rows = tx.getDataIterator(new DataIteratorContext()).stream().toList();
        }
        catch (RuntimeException e)
        {
            assertTrue("Expected a validation failure carrying '" + message + "' but got: " + e, causeChainHasValidationError(e, message));
            return;
        }
        fail("Expected stream to fail on invalid row a=" + invalidA + " but stream silently returned " + rows.size() + " of " + ROW_COUNT + " rows");
    }

    private static boolean causeChainHasValidationError(Throwable t, String message)
    {
        for (Throwable cur = t; cur != null; cur = cur.getCause() == cur ? null : cur.getCause())
        {
            if (cur instanceof BatchValidationException || (cur.getMessage() != null && cur.getMessage().contains(message)))
                return true;
        }
        return false;
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
}
