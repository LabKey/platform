package org.labkey.api.exp.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

public enum SampleMeasurementUnit
{
    g("grams", SampleMeasurementType.Mass, 1),
    mg("milligrams", SampleMeasurementType.Mass, 0.001),
    kg("kilograms", SampleMeasurementType.Mass, 1000),
    mL("milliliters", SampleMeasurementType.Volume, 1),
    uL("microliters", SampleMeasurementType.Volume, 0.001),
    L("liters", SampleMeasurementType.Volume, 1000),
    kL("kiloliters", SampleMeasurementType.Volume, 100000),
    unit("units", SampleMeasurementType.Count, 1);

    private final String _longLabel;
    private final SampleMeasurementType _type;
    private final double _ratio;

    SampleMeasurementUnit(String longLabel, SampleMeasurementType type, double ratio)
    {
        _longLabel = longLabel;
        _type = type;
        _ratio = ratio;
    }

    public String getLongLabel()
    {
        return _longLabel;
    }

    public SampleMeasurementType getType()
    {
        return _type;
    }

    public double getRatio()
    {
        return _ratio;
    }

    public boolean isCompatible(SampleMeasurementUnit otherUnit)
    {
        return otherUnit != null && this._type == otherUnit._type;
    }

    public static SampleMeasurementUnit getUnitFromLabel(String label)
    {
        if (label == null)
            return null;

        for (SampleMeasurementUnit unit : SampleMeasurementUnit.values())
        {
            if (label.equalsIgnoreCase(unit.getLongLabel()))
                return unit;
        }
        return null;
    }

    public static SampleMeasurementUnit getUnitFromName(String name)
    {
        if (name == null)
            return null;

        for (SampleMeasurementUnit unit : SampleMeasurementUnit.values())
        {
            if (name.equalsIgnoreCase(unit.name()))
                return unit;
        }
        return null;
    }

    public Double convertAmount(@Nullable Double amount,  @Nullable SampleMeasurementUnit targetUnit)
    {
        if (amount == null)
            return amount;

        if (targetUnit == null || targetUnit == this)
            return amount;

        return amount * (this.getRatio() / targetUnit.getRatio());
    }

    public static boolean isCompatible(String stringA, String stringB)
    {
        if (StringUtils.isEmpty(stringA) && StringUtils.isEmpty(stringB))
            return true;

        if (StringUtils.isEmpty(stringA) || StringUtils.isEmpty(stringB))
            return false;

        if (stringA.equals(stringB))
            return true;

        SampleMeasurementUnit unitA = getUnitFromName(stringA);
        SampleMeasurementUnit unitB = getUnitFromName(stringB);

        if (unitA == null ||unitB == null)
            return false;

        return unitA.getType() == unitB.getType();
    }

}
