/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.query.ValidationException;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public interface AssayRunCreator<ProviderType extends AssayProvider>
{
    /**
     * @return the batch to which the run has been assigned
     */
    public ExpExperiment saveExperimentRun(AssayRunUploadContext context, @Nullable ExpExperiment batch, ExpRun run, boolean forceSaveBatchProps)
        throws ExperimentException, ValidationException;

    DataValidator getDataValidator();
}
