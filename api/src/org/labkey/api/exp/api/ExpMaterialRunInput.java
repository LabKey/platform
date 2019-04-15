/*
 * Copyright (c) 2006-2018 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.jetbrains.annotations.Nullable;

/** Maps an {@link ExpMaterial} to be the input into an {@link ExpRun}. */
public interface ExpMaterialRunInput extends ExpRunInput
{
    String DEFAULT_ROLE = "Material";

    ExpMaterial getMaterial();

    /**
     * Get the {@link ExpMaterialProtocolInput} from original {@link ExpProtocol} the that this
     * input or output is associated with.
     */
    @Nullable ExpMaterialProtocolInput getProtocolInput();
}
