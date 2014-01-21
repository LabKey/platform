package org.labkey.api.data.statistics;

/**
 * Created by klum on 1/14/14.
 */
public interface StatsService
{
    enum CurveFitType
    {
        FOUR_PARAMETER("Four Parameter", "4pl"),
        FIVE_PARAMETER("Five Parameter", "5pl"),
        POLYNOMIAL("Polynomial", "poly");

        private String _label;
        private String _colSuffix;

        private CurveFitType(String label, String colSuffix)
        {
            _label = label;
            _colSuffix = colSuffix;
        }

        public String getColSuffix()
        {
            return _colSuffix;
        }

        public String getLabel()
        {
            return _label;
        }

        public static CurveFitType fromLabel(String label)
        {
            for (CurveFitType type : values())
            {
                if (type.getLabel().equals(label))
                    return type;
            }
            return null;
        }

        public static CurveFitType fromColSuffix(String suffix)
        {
            for (CurveFitType type : values())
            {
                if (type.getColSuffix().equals(suffix))
                    return type;
            }
            return null;
        }
    }

    enum AUCType
    {
        NORMAL("Normal"),
        POSITIVE("Positive"),
        NEGATIVE("Negative");

        private String _label;

        private AUCType(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }
    }

    /**
     * Factory to return a statistics instance for the specified data
     * @param data an array of data to compute statistics over
     * @return
     */
    MathStat getStats(double[] data);

    CurveFit getCurveFit(CurveFitType type, DoublePoint[] data);
}
