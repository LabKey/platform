/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;

/**
 * Indicates that a table can be configured to include data from multiple containers when it is queried. Tables
 * typically default to only showing data from the current container.
 * User: jgarms
 * Date: Dec 3, 2008
 */
public interface ContainerFilterable extends TableInfo
{
    void setContainerFilter(@NotNull ContainerFilter containerFilter);

    /** @return whether the default for this table has been overridden */
    boolean hasDefaultContainerFilter();
}
