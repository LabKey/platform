/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.di;

import com.drew.lang.annotations.Nullable;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.util.HeartBeat;
import org.labkey.di.pipeline.TransformUtils;

import static org.labkey.di.DataIntegrationQuerySchema.Columns.TransformModified;
import static org.labkey.di.DataIntegrationQuerySchema.Columns.TransformRunId;

/**
 * User: matthew
 * Date: 4/24/13
 * Time: 1:23 PM
 */
public class TransformDataIteratorBuilder implements DataIteratorBuilder
{
    final int _transformRunId;
    final DataIteratorBuilder _input;
    Logger _statusLogger = null;
    @NotNull
    PipelineJob _job;
    private final String _statusName;

    public TransformDataIteratorBuilder(int transformRunId, DataIteratorBuilder input, @Nullable Logger statusLogger, @NotNull PipelineJob job, String statusName)
    {
        _transformRunId = transformRunId;
        _input = input;
        _statusLogger = statusLogger;
        _job = job;
        _statusName = statusName;
    }


    static final CaseInsensitiveHashSet diColumns = new CaseInsensitiveHashSet();
    static
    {
        for (DataIntegrationQuerySchema.Columns c : DataIntegrationQuerySchema.Columns.values())
            diColumns.add(c.getColumnName());
    }


    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        DataIterator in = _input.getDataIterator(context);
        final int[] count = new int[] {0};
        SimpleTranslator out = new SimpleTranslator(in, context)
        {
            long lastChecked = HeartBeat.currentTimeMillis();

            @Override
            public boolean next() throws BatchValidationException
            {
                boolean r = super.next();
                if (r)
                {
                    // Check every 10 seconds to make sure we haven't been cancelled
                    if (HeartBeat.currentTimeMillis() - lastChecked > 10000)
                    {
                        lastChecked = HeartBeat.currentTimeMillis();
                        _job.setStatus(_statusName + " RUNNING", count[0] + " rows processed");
                    }

                    count[0]++;
                    if (0 == count[0] % 10000)
                    {
                        if (null != _statusLogger)
                        {
                            _statusLogger.info("" + count[0] + " rows transferred");
                        }
                    }
                }
                return r;
            }
        };

        for (int i=1 ; i<=in.getColumnCount() ; i++)
        {
            ColumnInfo c = in.getColumnInfo(i);
            if (diColumns.contains(c.getName()) || TransformUtils.isRowversionColumn(c))
                continue;
            out.addColumn(i);
        }
        out.addConstantColumn(TransformRunId.getColumnName(), JdbcType.INTEGER, _transformRunId);
        out.addTimestampColumn(TransformModified.getColumnName());

        return LoggingDataIterator.wrap(out);
    }
}