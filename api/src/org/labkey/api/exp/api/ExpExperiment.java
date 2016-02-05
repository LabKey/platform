/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import java.util.List;

/**
 * A grouping of {@link ExpRun}. "Batches" are a special type which restrict all runs
 * to be instances of the same {@link ExpProtocol}.
 */
public interface ExpExperiment extends ExpObject
{
    List<? extends ExpRun> getRuns();
    List<? extends ExpRun> getRuns(@Nullable ExpProtocol parentProtocol, ExpProtocol childProtocol);

    /**
     * If this experiment is a batch, it only allows runs that are of the same parent protocol.
     * @return the protocol for which this object is a batch. May be null.
     */
    ExpProtocol getBatchProtocol();

    /**
     * If this experiment is a batch, it only allows runs that are of the same parent protocol.
     * @param protocol the protocol for which this object is a batch. May be null.
     */
    void setBatchProtocol(ExpProtocol protocol);

    /**
     * @return all the parent protocols for all of the member runs 
     */
    List<? extends ExpProtocol> getAllProtocols();

    /**
     * Remove an ExpRun from this experiment.
     * If this experiment is a batch, the run is disconnected from the batch.
     * @param user The current user.
     * @param run The run to remove from this experiment.
     */
    void removeRun(User user, ExpRun run);

    /**
     * Add ExpRuns to this experiment.
     * If this experiment is a batch, the runs must all have the same ExpProtocol and be the batch's protocol.
     * @param user The current user.
     * @param run The runs to add to this experiment.
     */
    void addRuns(User user, ExpRun... run);

    boolean isHidden();


    /** Stored in the exp.experimentrun table */
    String getComments();
    /** Stored in the exp.experimentrun table */
    void setComments(String comments);
}
