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

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Extension point for DataClass-specific transformations in the pre-trigger DataIterator pipeline.
 * Registered per DataClass name via {@link org.labkey.api.exp.api.ExperimentService#registerDataClassDataIteratorTransformer}.
 * A fresh instance is created for each import operation since implementations may be stateful
 * between {@link #prepareTranslator} and {@link #wrapDataIterator}.
 */
public interface DataClassDataIteratorTransformer
{
    /**
     * Called during SimpleTranslator setup to inspect input columns and add placeholder columns
     * (e.g., via {@link SimpleTranslator#addNullColumn}) that will be populated by the wrapping DataIterator.
     *
     * @return true if the transformer is active and {@link #wrapDataIterator} should be called
     */
    boolean prepareTranslator(@NotNull SimpleTranslator step0,
                              @NotNull Map<String, Integer> inputColumnNameMap,
                              @NotNull DataIteratorContext context);

    /**
     * Wraps the DataIterator to apply DataClass-specific transformations.
     * Called after the pre-trigger pipeline is assembled, only if {@link #prepareTranslator} returned true.
     */
    @NotNull
    DataIterator wrapDataIterator(@NotNull DataIterator input, @NotNull DataIteratorContext context);
}
