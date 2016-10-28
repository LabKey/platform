/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.di.filters;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SimpleFilter;
import org.labkey.di.VariableMap;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.steps.StepMeta;
import org.labkey.etl.xml.FilterType;

/**
 * User: matthewb
 * Date: 6/20/13
 * Time: 2:16 PM
 */
public class SelectAllFilterStrategy extends FilterStrategyImpl
{

    public SelectAllFilterStrategy(StepMeta stepMeta, TransformJobContext context, DeletedRowsSource deletedRowsSource)
    {
        super(stepMeta, context, deletedRowsSource);
    }

    @Override
    public boolean hasWork()
    {
        init();
        return true;
    }

    @Override
    public SimpleFilter getFilter(VariableMap variables)
    {
        return null;
    }

    public static class Factory extends FilterStrategyFactoryImpl
    {
        public Factory(@Nullable FilterType ft)
        {
            super(ft);
        }

        @Override
        public FilterStrategy getFilterStrategy(TransformJobContext context, StepMeta stepMeta)
        {
            return new SelectAllFilterStrategy(stepMeta, context, _deletedRowsSource);
        }

        @Override
        public boolean checkStepsSeparately()
        {
            return false;
        }

        @Override
        public Type getType()
        {
            return Type.SelectAll;
        }
    }
}
