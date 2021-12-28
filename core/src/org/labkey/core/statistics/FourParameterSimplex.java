package org.labkey.core.statistics;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;

/**
 * Four parameter curve fitting which builds a simplex of n-dimensions to converge on an optimal
 * non-linear fit for arbitrary parameters. This class will optimize fit errors using both the Powell
 * and Nelder Mead algorithms and will select the optimal results based on a variety
 * of starting points.
 */
public class FourParameterSimplex extends ParameterCurveFit implements MultivariateFunction
{
    private static final Logger _log = LogManager.getLogger(FourParameterSimplex.class);

    private static final double REL_TOLERANCE = 1.0e-10;            // relative convergence factor
    private static final double ABS_TOLERANCE = 1.0e-20;            // absolute convergence factor
    private static final int MAX_EVALUATIONS = 10000;               // max number of evaluations

    private double _minError;
    private double[] _bestFit;
    private MultivariateOptimizer _lastOptimizer;

    public FourParameterSimplex(DoublePoint[] data)
    {
        super(data, StatsService.CurveFitType.FOUR_PARAMETER_SIMPLEX);
    }

    @Override
    protected SigmoidalParameters calculateFitParameters(double minValue, double maxValue)
    {
        double[] start = new double[4];

        // initialize
        _minError = 1e10;
        _bestFit = new double[4];

        for (int i=0; i < 50; i++)
        {
            double min = minValue / 1+1;
            double max = maxValue * 1+i;
            double mid = min + (0.5 * (max - min));
            double interpolated = getInterpolatedCutoffXValue(mid);
            if (!Double.isNaN(interpolated) && !Double.isInfinite(interpolated))
                mid = interpolated;

            start[0] = min;     // starting min
            start[1] = max;     // starting max
            start[2] = mid;     // inflection
            start[3] = .5;      // slope

            try
            {
                SimplexOptimizer simplexOptimizer = new SimplexOptimizer(REL_TOLERANCE, ABS_TOLERANCE);
                PowellOptimizer powellOptimizer = new PowellOptimizer(REL_TOLERANCE, ABS_TOLERANCE);

                optimize(simplexOptimizer, start);
                optimize(powellOptimizer, start);
            }
            catch (TooManyEvaluationsException e)
            {
                _log.info(e.getMessage());
            }
        }

        _log.info("optimizer : " + _lastOptimizer + " fit error : " + calculateFitError(createParams(_bestFit)));
        _log.info("params " + createParams(_bestFit).toMap());

        return createParams(_bestFit);
    }

    /**
     * Optimize the simplex using the initial starting points and update the best fit
     */
    private void optimize(MultivariateOptimizer optimizer, double[] start)
    {
        NelderMeadSimplex simplex = new NelderMeadSimplex(4);
        PointValuePair optimum = optimizer.optimize(
                new MaxEval(MAX_EVALUATIONS),
                new ObjectiveFunction(this),
                simplex,
                GoalType.MINIMIZE,
                new InitialGuess(start));

        if (optimum.getValue() < _minError)
        {
            _minError = optimum.getValue();
            _bestFit = optimum.getPoint().clone();
            _lastOptimizer = optimizer;
        }
    }

    protected double calculateFitError(SigmoidalParameters parameters)
    {
        double deviationValue = 0;
        double varianceValue = 0;
        double total = 0;

        // find the mean
        for (DoublePoint point : getData())
        {
            total += point.getY();
        }
        double mean = total / getData().length;

        for (DoublePoint point : getData())
        {
            double expectedValue = point.getY();
            double foundValue = fitCurve(point.getX(), parameters);
            deviationValue += Math.pow(foundValue - expectedValue, 2);
            varianceValue += Math.pow(expectedValue - mean, 2);
        }

        return 1 - deviationValue / varianceValue;
    }

    @Override
    public double value(double[] point)
    {
        // compute the sum of the squares of the residuals at this point
        // the optimizers are configured to converge to a minimal value
        return sumSquares(point);
    }

    private double sumSquares(double[] params)
    {
        SigmoidalParameters parameters = createParams(params);
        double sumSq = 0;
        for (DoublePoint point : getData())
        {
            double expectedValue = point.getY();
            double foundValue = fitCurve(point.getX(), parameters);

            sumSq += Math.pow(foundValue - expectedValue, 2);
        }
        return sumSq;
    }

    private SigmoidalParameters createParams(double[] params)
    {
        SigmoidalParameters parameters = new SigmoidalParameters();

        parameters.min = params[0];
        parameters.max = params[1];
        parameters.inflection = params[2];
        parameters.slope = params[3];

        return parameters;
    }

    @Override
    public double solveForX(double y)
    {
        try
        {
            SigmoidalParameters params = getParameters();
            if (params != null)
            {
                double inter = ((params.getMin() - params.getMax()) / (y - params.getMax())) - 1;

                return params.getInflection() * Math.pow(inter, 1 / params.getSlope());
            }
            throw new IllegalArgumentException("No curve fit parameters for " + getType().name());
        }
        catch (FitFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double fitCurve(double x, SigmoidalParameters params)
    {
        if (params != null)
        {
            return params.getMax() + ((params.getMin() - params.getMax()) /
                    (1 + Math.pow(x/params.getInflection(), params.getSlope())));
        }
        throw new IllegalArgumentException("No curve fit parameters for " + getType());
    }
}
