package org.labkey.api.assay.dilution;

import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.WellGroup;

import java.util.List;

/**
 * Created by klum on 10/10/2014.
 */
public class EmptyCurveImpl extends WellGroupCurveImpl
{
    public EmptyCurveImpl(List<WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
    {
        super(wellGroups, assumeDecreasing, percentCalculator, StatsService.CurveFitType.NONE);
    }

    @Override
    protected CurveFit createCurveFit(StatsService.CurveFitType type) throws FitFailedException
    {
        ensureWellSummaries();
        DoublePoint[] data = new DoublePoint[_wellSummaries.length];
        int i=0;
        for (WellSummary well : _wellSummaries)
        {
            // we want to express the neutralization values as percentages
            data[i++] = new DoublePoint(well.getDilution(), well.getNeutralization() * 100);
        }
        StatsService service = ServiceRegistry.get().getService(StatsService.class);
        return service.getCurveFit(type, data);
    }
}
