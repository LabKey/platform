/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.security.User;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** An instance of an {@link ExpProtocol}, with actual inputs and outputs */
public interface ExpRun extends ExpObject
{
    /** @return the experiments (AKA run groups in the UI) of which this run is a member */
    List<? extends ExpExperiment> getExperiments();

    @Nullable ExpExperiment getBatch();
    ExpProtocol getProtocol();

    boolean isFinalOutput(ExpData data);

    /**
     * @param type an optional filter for the type of data
     */
    List<? extends ExpData> getOutputDatas(@Nullable DataType type);
    /**
     * @param inputRole if null, don't filter by input role. If non-null, filter to only the specified role
     * @param appType if null, don't filter by type. If non-null, filter to only the specified type
     */
    List<? extends ExpData> getInputDatas(@Nullable String inputRole, @Nullable ExpProtocol.ApplicationType appType);
    File getFilePathRoot();
    void setFilePathRoot(File filePathRoot);
    Path getFilePathRootPath();
    void setFilePathRootPath(Path filePathRoot);
    void setProtocol(ExpProtocol protocol);
    void setJobId(Integer jobId);
    ExpProtocolApplication addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType type, String name);
    
    /** Stored in the exp.experimentrun table */
    // TODO - Merge this with getComment() (backed by ontology manager) on ExpObject
    String getComments();
    /** Stored in the exp.experimentrun table */
    void setComments(String comments);

    void setEntityId(String entityId);
    String getEntityId();

    /** @return map from material object to role name. Multiple inputs might use the same role name, hence the direction of the map */
    Map<ExpMaterial, String> getMaterialInputs();

    /** @return map from data object to role name. Multiple inputs might use the same role name, hence the direction of the map */
    Map<ExpData, String> getDataInputs();

    /**
     * @return all of the materials objects marked as outputs of this run.
     * This may be a subset of all of the materials that were created by the run if some were intermediate/transient.
     */
    List<ExpMaterial> getMaterialOutputs();

    /**
     * @return all of the data objects marked as outputs of this run.
     * This may be a subset of all of the files that were created by the run if some were intermediate/transient.
     */
    List<ExpData> getDataOutputs();

    /**
     * @return all of the data objects referenced by this run,
     * including top-level inputs and outputs, as well as intermediate files
     */
    List<? extends ExpData> getAllDataUsedByRun();
    Integer getJobId();

    List<? extends ExpProtocolApplication> getProtocolApplications();

    /** Get the protocol application that marks all of the inputs to the run as a whole */
    ExpProtocolApplication getInputProtocolApplication();

    /** Get the protocol application that marks all of the outputs to the run as a whole */
    ExpProtocolApplication getOutputProtocolApplication();

    void deleteProtocolApplications(User user);

    /** Mark this run and its data as being replaced (superceeded) by another, more current run */
    void setReplacedByRun(ExpRun run);

    /** @return the run that represents the updated version of this run's data, if any */
    ExpRun getReplacedByRun();

    /**
     * @return the runs that are being marked as being replaced by this run. In almost all cases this is zero or one
     * run, but it could be multiple
     */
    List<? extends ExpRun> getReplacesRuns();

    /** Archive data files associated with the run, primarily used when a file is deleted. */
    void archiveDataFiles(User user);

    void setCreated(Date created);
    void setCreatedBy(User user);

}
