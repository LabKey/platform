/*
 * Copyright (c) 2015 LabKey Corporation
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
import org.labkey.etl.xml.DeletedRowsSourceObjectType;
import org.labkey.etl.xml.FilterType;

/**
 * User: tgaluhn
 * Date: 4/21/2015
 */
public abstract class FilterStrategyFactoryImpl implements FilterStrategy.Factory
{
    protected final DeletedRowsSource _deletedRowsSource;

    public FilterStrategyFactoryImpl(@Nullable FilterType ft)
    {
        if (null == ft || null == ft.getDeletedRowsSource())
            _deletedRowsSource = null;
        else _deletedRowsSource = initDeletedRowsSource(ft.getDeletedRowsSource());
    }

    private DeletedRowsSource initDeletedRowsSource(DeletedRowsSourceObjectType s)
    {
        return new DeletedRowsSource(s.getSchemaName(), s.getQueryName(), s.getTimestampColumnName(), s.getRunColumnName(), s.getDeletedSourceKeyColumnName(), s.getTargetKeyColumnName());
    }
}
