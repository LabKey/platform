package org.labkey.core.statistics;

import org.labkey.api.assay.dilution.ParameterCurveImpl;
import org.labkey.api.assay.dilution.PolynomialCurveImpl;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.view.Stats;
import org.labkey.core.statistics.PolynomialCurveFit;

/**
 * Created by klum on 1/14/14.
 */
public class StatsServiceImpl implements StatsService
{
    @Override
    public MathStat getStats(double[] data)
    {
        return new Stats.DoubleStats(data);
    }

    @Override
    public CurveFit getCurveFit(CurveFitType type, DoublePoint[] data)
    {
        switch (type)
        {
            case FOUR_PARAMETER:
            case FIVE_PARAMETER:
                return new ParameterCurveFit(data, type);
            case POLYNOMIAL:
                return new PolynomialCurveFit(data);
        }
        throw new IllegalArgumentException("Unable to find a CurveFit implementation for type: " + type.name());
    }
}
