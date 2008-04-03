package org.labkey.api.study;

import org.labkey.common.util.Pair;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:58:12 PM
 */
public interface DilutionCurve
{
    public enum FitType
    {
        FOUR_PARAMETER("Four Parameter"),
        FIVE_PARAMETER("Five Parameter");

        private String _label;
        private FitType(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public static FitType fromLabel(String label)
        {
            for (FitType type : values())
            {
                if (type.getLabel().equals(label))
                    return type;
            }
            return null;
        }
    }

    DoublePoint[] getCurve();

    double getFitError();

    double getCutoffDilution(double percent);

    double getInterpolatedCutoffDilution(double percent);

    double getMinDilution();

    double getMaxDilution();

    public static class DoublePoint extends Pair<Double, Double>
    {
        public DoublePoint(double x, double y)
        {
            super(x, y);
        }

        public double getX()
        {
            return getKey();
        }

        public double getY()
        {
            return getValue();
        }
    }

    public static interface PercentCalculator
    {
        double getPercent(WellGroup group, WellData data);
    }
}
