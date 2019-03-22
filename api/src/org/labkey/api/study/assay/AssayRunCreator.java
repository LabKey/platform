/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;

/**
 * An AssayRunCreator does the actual work of constructing an assay run and saving it to the database. It gets
 * data about the run to be created from a AssayRunUploadContext and translates it into objects in the Experiment
 * module and its schema. The work of actually importing from a data file is typically handled by an
 * ExperimentDataHandler.
 *
 * User: jeckels
 * Date: Oct 12, 2011
 */
public interface AssayRunCreator<ProviderType extends AssayProvider>
{
    /**
     * Create and save an experiment run synchronously or asynchronously in a background job depending upon the assay design.
     *
     * @param context The context used to create and save the batch and run.
     * @param batchId if not null, the run group that's already created for this batch. If null, a new one will be created.
     * @return Pair of batch and run that were inserted.  ExpBatch will not be null, but ExpRun may be null when inserting the run async.
     */
    public Pair<ExpExperiment, ExpRun> saveExperimentRun(AssayRunUploadContext<ProviderType> context, @Nullable Integer batchId)
            throws ExperimentException, ValidationException;

    /**
     * @return the batch to which the run has been assigned
     */
    public ExpExperiment saveExperimentRun(AssayRunUploadContext<ProviderType> context, @Nullable ExpExperiment batch, ExpRun run, boolean forceSaveBatchProps)
        throws ExperimentException, ValidationException;
}
