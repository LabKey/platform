/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.study.CohortFilter;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitMapKey;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

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
    public Map<VisitMapKey, Integer> getVisitSummary(CohortFilter cohortFilter, QCStateSet qcStates) throws SQLException
    {
        return Collections.emptyMap();
    }

    public VisitImpl findVisitBySequence(double seq)
    {
        return null;
    }

    public boolean isVisitOverlapping(VisitImpl visit) throws SQLException
    {
        throw new UnsupportedOperationException("Study has no timepoints");
    }


    
}
