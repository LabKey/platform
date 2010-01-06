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
