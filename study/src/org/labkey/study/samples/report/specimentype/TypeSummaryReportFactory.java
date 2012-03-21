package org.labkey.study.samples.report.specimentype;

import org.labkey.study.samples.report.SpecimenVisitReport;
import org.labkey.study.samples.report.SpecimenTypeVisitReport;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.api.data.SimpleFilter;

import java.util.List;
import java.util.Collections;

/**
 * User: brittp
 * Created: Jan 24, 2008 1:38:06 PM
 */
public class TypeSummaryReportFactory extends TypeReportFactory
{
    public String getLabel()
    {
        return "Summary Report";
    }

    protected List<? extends SpecimenVisitReport> createReports()
    {
        VisitImpl[] visits = SampleManager.getInstance().getVisitsWithSpecimens(getContainer(), getUser(), getCohort());
        SimpleFilter filter = new SimpleFilter();
        addBaseFilters(filter);
        SpecimenTypeVisitReport report = new SpecimenTypeVisitReport("Summary", visits, filter, this);
        return Collections.singletonList(report);
    }

    public Class<? extends SpecimenController.SpecimenVisitReportAction> getAction()
    {
        return SpecimenController.TypeSummaryReportAction.class;
    }
}
