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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/*
* User: jeckels
* Date: Jul 28, 2008
*/
public interface ExpProtocolOutput extends ExpObject
{
    @Nullable
    String getDescription();

    /** Convenience and NPE-avoiding method for getSourceApplication().getProtocol() */
    ExpProtocol getSourceProtocol();

    @NotNull
    Collection<String> getAliases();

    /** @return the ExpRun that claims this object as an output. That is, the one that created it. */
    @Nullable
    ExpRun getRun();

    /**
     * @return the id of the run that claims this object as an output.
     * That is, the one that created it. In most cases, use getRun() instead - this method avoids the creation of the
     * ExpRun and therefore is faster for performance-critical scenarios
     */
    Integer getRunId();

    /** @return all of the protocol applications that reference this data/material as input */
    List<? extends ExpProtocolApplication> getTargetApplications();
    /** @return all of the protocol applications that reference this data/material as input */
    List<? extends ExpRun> getTargetRuns();

    void setSourceApplication(ExpProtocolApplication sourceApplication);
    /**
     * @return the ExpProtocolApplication that claims this object as an output.
     * That is, the one that created it, and part of the ExpRun identified by getRun()
     */
    @Nullable
    ExpProtocolApplication getSourceApplication();

    void setRun(ExpRun run);

    List<ExpProtocolApplication> getSuccessorApps();

    List<ExpRun> getSuccessorRuns();

    String getCpasType();
    void setCpasType(String type);
}
