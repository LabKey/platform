package org.labkey.study.reports;

import org.labkey.api.study.Visit;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * Assay progress report information that is sourced from a custom query
 */
public class QueryAssayProgressSource implements AssayProgressReport.AssayData
{
    @Override
    public List<Pair<AssayProgressReport.ParticipantVisit, String>> getSpecimenStatus(ViewContext context)
    {
        return null;
    }

    @Override
    public List<String> getParticipants(ViewContext context)
    {
        return null;
    }

    @Override
    public List<Visit> getVisits(ViewContext context)
    {
        return null;
    }
}
