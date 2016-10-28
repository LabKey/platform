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
import org.labkey.api.data.TableInfo;
import org.labkey.di.VariableMap;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.steps.StepMeta;

import java.io.Serializable;

/**
 * User: matthew
 * Date: 4/22/13
 * Time: 11:55 AM
 *
 *
 * The Filter strategy combines two pieces of functionality.
 *
 * 1) checker: determines whether there is incremental work to perform
 * 2) filter: select a subset of rows to process
 *
 */
public interface FilterStrategy
{
    enum Type
    {
        SelectAll,
        ModifiedSince,
        Run
    }

    interface Factory extends Serializable
    {
        FilterStrategy getFilterStrategy(TransformJobContext context, StepMeta stepMeta);
        boolean checkStepsSeparately();
        Type getType();
    }

    boolean hasWork();

    /**
     * Has side effect of setting parameters
     */
    SimpleFilter getFilter(VariableMap variables);
    SimpleFilter getFilter(VariableMap variables, boolean deleting);
    @Nullable DeletedRowsSource getDeletedRowsSource();
    @Nullable TableInfo getDeletedRowsTinfo();
    @Nullable String getDeletedRowsKeyCol();
    @Nullable String getTargetDeletionKeyCol();
    void setTargetDeletionKeyCol(@Nullable String col);
    String getLogMessage(@Nullable String filterValue);
}