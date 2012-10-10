/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

public interface ExpExperiment extends ExpObject
{
    ExpRun[] getRuns();
    ExpRun[] getRuns(@Nullable ExpProtocol parentProtocol, ExpProtocol childProtocol);

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
    List<ExpProtocol> getAllProtocols();
    void removeRun(User user, ExpRun run);
    void addRuns(User user, ExpRun... run);
    boolean isHidden();


    /** Stored in the exp.experimentrun table */
    public String getComments();
    /** Stored in the exp.experimentrun table */
    public void setComments(String comments);

}
