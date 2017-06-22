/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.di.columnTransform.ColumnTransform;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.util.HeartBeat;
import org.labkey.di.pipeline.TransformPipelineJob;
import org.labkey.di.pipeline.TransformUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.di.DataIntegrationQuerySchema.Columns.TransformCreated;
import static org.labkey.di.DataIntegrationQuerySchema.Columns.TransformCreatedBy;
import static org.labkey.di.DataIntegrationQuerySchema.Columns.TransformModified;
import static org.labkey.di.DataIntegrationQuerySchema.Columns.TransformModifiedBy;
import static org.labkey.di.DataIntegrationQuerySchema.Columns.TransformRunId;

/**
 * User: matthew
 * Date: 4/24/13
 * Time: 1:23 PM
 */
public class TransformDataIteratorBuilder implements DataIteratorBuilder
{
    private final int _transformRunId;
    private final DataIteratorBuilder _input;
    private Logger _statusLogger = null;
    @NotNull
    private PipelineJob _job;
    private final String _statusName;
    private final Map<String, List<ColumnTransform>> _columnTransforms;
    private final Map<ParameterDescription, Object> _constants;
    private final Set<String> _alternateKeys;

    public TransformDataIteratorBuilder(int transformRunId, DataIteratorBuilder input, @Nullable Logger statusLogger, @NotNull PipelineJob job, String statusName, Map<String, List<ColumnTransform>> columnTransforms, Map<ParameterDescription, Object> constants, Set<String> alternateKeys)
    {
        _transformRunId = transformRunId;
        _input = input;
        _statusLogger = statusLogger;
        _job = job;
        _statusName = statusName;
        _columnTransforms = columnTransforms;
        _constants = constants;
        _alternateKeys = alternateKeys;
    }


    // The special diColumn values are set automatically by the ETL job if they appear in the target
    private static final CaseInsensitiveHashSet diColumns = new CaseInsensitiveHashSet();
    static
    {
        for (DataIntegrationQuerySchema.Columns c : DataIntegrationQuerySchema.Columns.values())
            diColumns.add(c.getColumnName());
    }

    // LabKey built-in columns, other than container, are allowed to pass through from source
    private static final CaseInsensitiveHashSet passThroughAllowedColumns = new CaseInsensitiveHashSet();
    static
    {
        for (SimpleTranslator.SpecialColumn c : SimpleTranslator.SpecialColumn.values())
        {
            if (!SimpleTranslator.SpecialColumn.Container.equals(c))
                passThroughAllowedColumns.add(c.name());
        }
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

        context.getAlternateKeys().addAll(_alternateKeys);

        Set<String> constantNames = _constants.keySet().stream().map(ParameterDescription::getName).collect(Collectors.toCollection(CaseInsensitiveHashSet::new));

        final Set<ColumnInfo> outCols = new HashSet<>();
        for (int i=1 ; i<=in.getColumnCount() ; i++)
        {
            ColumnInfo c = in.getColumnInfo(i);
            if (diColumns.contains(c.getName()) || TransformUtils.isRowversionColumn(c) || constantNames.contains(c.getName()))
                continue;
            // Add any transforms for this source column
            if (_columnTransforms.containsKey(c.getColumnName()))
            {
                for (ColumnTransform ct : _columnTransforms.get(c.getColumnName()))
                {
                    outCols.addAll(ct.addTransform(((TransformPipelineJob)_job).getTransformJobContext(), out, _transformRunId, i));
                }
            }
            else
            {
                // Just add the column as a passthrough from source to target
                ColumnInfo passThruCol = out.getColumnInfo(out.addColumn(i));
                passThruCol.setAlias(in.getColumnInfo(i).getAlias());
                outCols.add(passThruCol);
            }
        }
        // Now add the transforms that didn't specify a source column
        if (_columnTransforms.containsKey(null))
        {
            for (ColumnTransform ct : _columnTransforms.get(null))
            {
                outCols.addAll(ct.addTransform(((TransformPipelineJob)_job).getTransformJobContext(), out, _transformRunId, null));
            }
        }

        // If any of the LK built in columns (other than container) appear in the source query or
        // have been added by a column transform, allow them to pass through.
        outCols.stream().filter(outCol -> passThroughAllowedColumns.contains(outCol.getName()))
                .forEach(outCol -> context.getPassThroughBuiltInColumnNames().add(outCol.getName()));

        _constants.entrySet().stream().filter(e -> !diColumns.contains(e.getKey().getName())).forEach(e ->
                out.addConstantColumn(e.getKey().getName(), e.getKey().getJdbcType(), e.getValue()));

        // If the created and createdBy columns aren't coming from the source, don't modify them on updates
        if (!context.getPassThroughBuiltInColumnNames().contains(SimpleTranslator.SpecialColumn.Created.name()))
            context.getDontUpdateColumnNames().add(SimpleTranslator.SpecialColumn.Created.name());
        if (!context.getPassThroughBuiltInColumnNames().contains(SimpleTranslator.SpecialColumn.CreatedBy.name()))
            context.getDontUpdateColumnNames().add(SimpleTranslator.SpecialColumn.CreatedBy.name());

        out.addConstantColumn(TransformRunId.getColumnName(), JdbcType.INTEGER, _transformRunId);
        out.addTimestampColumn(TransformModified.getColumnName());
        out.addConstantColumn(TransformModifiedBy.getColumnName(), JdbcType.INTEGER, _job.getUser().getUserId());
        out.addTimestampColumn(TransformCreated.getColumnName());
        context.getDontUpdateColumnNames().add(TransformCreated.getColumnName());
        out.addConstantColumn(TransformCreatedBy.getColumnName(), JdbcType.INTEGER, _job.getUser().getUserId());
        context.getDontUpdateColumnNames().add(TransformCreatedBy.getColumnName());

        return LoggingDataIterator.wrap(out);
    }
}