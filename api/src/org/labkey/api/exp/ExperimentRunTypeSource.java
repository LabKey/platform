/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.exp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;

import java.util.Set;

/**
 * User: jeckels
 * Date: Dec 10, 2008
 */
public interface ExperimentRunTypeSource
{
    /**
     * Gets the run types that are relevant for the specified container
     * @param container scope for the run types. If null, implementations should return all staticly-defined run types,
     *                  but should not include ones scoped to a particular container (like those backed by specific assay
     *                  designs)
     * @return all the run types that may be present in the container
     */
    @NotNull Set<ExperimentRunType> getExperimentRunTypes(@Nullable Container container);
}