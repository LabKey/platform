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

package org.labkey.core.admin;

import org.labkey.api.action.SpringActionController.ActionStats;
import org.labkey.api.admin.ActionsHelper;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.util.UnexpectedException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Invoked actions only, heaviest connection borrowers first */
public class ConnectionUsageTsvWriter extends TSVWriter
{
    private record Row(String module, String controller, String action, ActionStats stats) {}

    @Override
    protected void writeColumnHeaders()
    {
        writeLine(Arrays.asList("module", "controller", "action", "invocations", "cumulative", "borrows", "borrowsPerInvocation",
            "holdMs", "holdPercent", "connectionMs", "maxConcurrent", "acquireMs", "unreturned"));
    }

    @Override
    protected int writeBody()
    {
        List<Row> rows;

        try
        {
            rows = ActionsHelper.getActionStatistics().entrySet().stream()
                .flatMap(module -> module.getValue().entrySet().stream()
                    .flatMap(controller -> controller.getValue().entrySet().stream()
                        .map(action -> new Row(module.getKey(), controller.getKey(), action.getKey(), action.getValue()))))
                .filter(row -> row.stats().getCount() > 0)
                .sorted(Comparator.comparingLong((Row row) -> row.stats().getBorrows())
                    .thenComparingLong(row -> row.stats().getCount())
                    .reversed())
                .toList();
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

        for (Row row : rows)
        {
            ActionStats stats = row.stats();
            writeLine(Arrays.asList(
                row.module(),
                row.controller(),
                row.action(),
                String.valueOf(stats.getCount()),
                String.valueOf(stats.getElapsedTime()),
                String.valueOf(stats.getBorrows()),
                String.format(Locale.ROOT, "%.2f", stats.getBorrowsPerInvocation()),
                String.valueOf(stats.getConnectionWallTime()),
                String.format(Locale.ROOT, "%.1f", stats.getConnectionHoldFraction() * 100),
                String.valueOf(stats.getConnectionHoldTime()),
                String.valueOf(stats.getMaxConcurrent()),
                String.valueOf(stats.getAcquireTime()),
                String.valueOf(stats.getUnreturned())
            ));
        }

        return rows.size();
    }
}
