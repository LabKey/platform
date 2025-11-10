package org.labkey.core.statistics;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.statistics.DoublePoint;

import static org.labkey.api.data.statistics.StatsService.CurveFitType.FIVE_PARAMETER_ALT;

/*
* Equation: Asymmetrical Sigmoidal, 5PL (aka Richards five-parameter dose-response curve)
* Y = Bottom + (Numerator/Denominator)
* Numerator = Top - Bottom
* Denominator = (1+(2^(1/S)-1)*((EC50/X)^HillSlope))^S
*/
public class FiveParameterCurveFit extends ParameterCurveFit
{
    public FiveParameterCurveFit(DoublePoint[] data, @Nullable Double asymptoteMin, @Nullable Double asymptoteMax)
    {
        super(data, FIVE_PARAMETER_ALT, asymptoteMin, asymptoteMax);
    }

    @Override
    public double solveForX(double y)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public double fitCurve(double x, SigmoidalParameters params)
    {
        if (params != null)
        {
            double topMinusBottom = params.getMax() - params.getMin();
            double exponent = 1.0 / params.getAsymmetry();
            double base = Math.pow(2.0, exponent) - 1.0;
            double ec50OverXPowerHillSlope = Math.pow(params.getInflection() / x, params.getSlope());
            double denominator = Math.pow(1.0 + (base * ec50OverXPowerHillSlope), params.getAsymmetry());
            return params.getMin() + (topMinusBottom / denominator);
        }
        throw new IllegalArgumentException("No curve fit parameters for " + _fitType.name());
    }
}
