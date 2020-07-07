/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.study.ParticipantVisit;

import java.util.Date;

/**
 * Helps resolve and validate information during an assay's copy-to-study operation. Since each row will need to be
 * associated with a participant and date/visit to be incorporated into the target study dataset, different
 * implementations may use other context to automate filling them in. For example, an implementation could use a
 * specimen ID column that's in the assay data to find the associated specimen in the target study and automatically
 * use its participant and date/visit values.
 * User: jeckels
 * Date: Sep 17, 2007
 */
public interface ParticipantVisitResolver
{
    Container getRunContainer();
    @NotNull
    ParticipantVisit resolve(String specimenID, String participantID, Double visitID, Date date, Container resultDomainTargetStudy) throws ExperimentException;
}
