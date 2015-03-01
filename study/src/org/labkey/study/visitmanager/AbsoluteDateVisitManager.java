/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

package org.labkey.study.visitmanager;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.security.User;
import org.labkey.study.CohortFilter;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitMapKey;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: Dec 30, 2009 12:13:57 PM
 */
public class AbsoluteDateVisitManager extends RelativeDateVisitManager
{
    public AbsoluteDateVisitManager(StudyImpl study)
    {
        super(study);
    }

    @Override
    public String getLabel()
    {
        return "[no-visit]"; // XXX: for checking all the places in the UI where the label shows up
    }

    @Override
    public String getPluralLabel()
    {
        return "[no-visits]"; // XXX: for checking all the places in the UI where the label shows up
    }

    @Override
    public Map<VisitMapKey, VisitStatistics> getVisitSummary(User user, CohortFilter cohortFilter, QCStateSet qcStates, Set<VisitStatistic> stats, boolean showAll)
    {
        return Collections.emptyMap();
    }

    @Override
    protected SQLFragment getVisitSummarySql(User user, CohortFilter cohortFilter, QCStateSet qcStates, String stats, String alias, boolean showAll)
    {
        throw new IllegalStateException("Should not be called");
    }

    public VisitImpl findVisitBySequence(double seq)
    {
        return null;
    }

    public boolean isVisitOverlapping(VisitImpl visit)
    {
        throw new UnsupportedOperationException("Study has no timepoints");
    }

    @Override
    protected void updateParticipantVisitTable(@Nullable User user, @Nullable Logger logger)
    {
        // no-op
    }

    @Override
    protected void updateVisitTable(User user, @Nullable Logger logger)
    {
        // no-op
    }
}
