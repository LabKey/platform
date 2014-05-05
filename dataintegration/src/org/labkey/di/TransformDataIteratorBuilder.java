/*
 * Copyright (c) 2013 LabKey Corporation
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

import static org.labkey.di.DataIntegrationQuerySchema.Columns.*;

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
    PipelineJob _job;

    public TransformDataIteratorBuilder(int transformRunId, DataIteratorBuilder input, @Nullable Logger statusLogger, PipelineJob job)
    {
        _transformRunId = transformRunId;
        _input = input;
        _statusLogger = statusLogger;
        _job = job;
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
            @Override
            public boolean next() throws BatchValidationException
            {
                boolean r = super.next();
                if (r)
                {
                    count[0]++;
                    if ( 0 == count[0] % 10000)
                    {
                        if (null != _job && _job.isCancelled())
                        {
                            getGlobalError().addGlobalError("Job cancelled");
                            return false;
                        }
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
            if (diColumns.contains(c.getName()))
                continue;
            out.addColumn(i);
        }
        out.addConstantColumn(TransformRunId.getColumnName(), JdbcType.INTEGER, _transformRunId);
        out.addTimestampColumn(TransformModified.getColumnName());

        return LoggingDataIterator.wrap(out);
    }
}