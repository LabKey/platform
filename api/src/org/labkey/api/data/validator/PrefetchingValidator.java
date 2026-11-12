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
package org.labkey.api.data.validator;

import org.labkey.api.exp.property.ValidatorContext;

import java.util.Collection;

/**
 * A column validator that resolves values in bulk, so {@link org.labkey.api.dataiterator.ValidatorIterator} reads a
 * window of rows ahead and prefetches them before validating each one
 */
public interface PrefetchingValidator extends ColumnValidator
{
    /** Resolves many values at once, so validating them runs no queries */
    void prefetch(Collection<?> values, ValidatorContext validatorContext);
}
