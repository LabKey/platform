package org.labkey.api.data.measurement;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConversionExceptionWithMessage;

public class Measurement
{
    private Unit _units;
    private Double _amount;
    private Unit _normalizingUnits;

    public enum Kind
    {
        Mass,
        Volume,
        Count
    }

    public enum Unit
    {
        g("grams", Kind.Mass, 1),
        mg("milligrams", Kind.Mass, 0.001),
        kg("kilograms", Kind.Mass, 1000),
        mL("milliliters", Kind.Volume, 1),
        uL("microliters", Kind.Volume, 0.001),
        L("liters", Kind.Volume, 1000),
        kL("kiloliters", Kind.Volume, 100000),
        unit("units", Kind.Count, 1);

        private final String _longLabel;
        private final Kind _kind;
        private final double _ratio;

        Unit(String longLabel, Kind kind, double ratio)
        {
            _longLabel = longLabel;
            _kind = kind;
            _ratio = ratio;
        }

        public String getLongLabel()
        {
            return _longLabel;
        }

        public Kind getKind()
        {
            return _kind;
        }

        public double getRatio()
        {
            return _ratio;
        }

        public boolean isCompatible(Unit otherUnit)
        {
            return otherUnit != null && this._kind == otherUnit._kind;
        }

        public static Unit getUnit(String unitString)
        {
            Unit unit = getUnitFromName(unitString);
            if (unit != null)
                return unit;
            return getUnitFromLabel(unitString);
        }

        public static Unit getUnitFromLabel(String label)
        {
            if (label == null)
                return null;

            for (Unit unit : Unit.values())
            {
                if (label.equalsIgnoreCase(unit.getLongLabel()))
                    return unit;
            }
            return null;
        }

        public static Unit getUnitFromName(String name)
        {
            if (name == null)
                return null;

            for (Unit unit : Unit.values())
            {
                if (name.equalsIgnoreCase(unit.name()))
                    return unit;
            }
            return null;
        }

        public Double convertAmount(@Nullable Double amount, @Nullable Measurement.Unit targetUnit)
        {
            if (amount == null)
                return amount;

            if (targetUnit == null || targetUnit == this)
                return amount;

            if (!this.isCompatible(targetUnit))
                throw new IllegalArgumentException("The target unit (" + targetUnit + ") is not compatible with the given unit (" + this + ").");

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

            Unit unitA = getUnitFromName(stringA);
            Unit unitB = getUnitFromName(stringB);

            if (unitA == null ||unitB == null)
                return false;

            return unitA.getKind() == unitB.getKind();
        }
    }

    public Measurement(Object amountObj, String units, @Nullable String normalizingUnits) throws ConversionExceptionWithMessage
    {
        _amount = convertToAmount(amountObj);
        _normalizingUnits = Unit.getUnit(normalizingUnits);
        validateUnits(units);
        _units = Unit.getUnit(units);
    }

    public Measurement(Object amountObj, String units)
    {
       this(amountObj, units, null);
    }

    public void validateUnits(String unitsStr) throws ConversionExceptionWithMessage
    {
        getUnits(unitsStr, _normalizingUnits);
    }

    public Double getNormalizedAmount()
    {
        // if there's no unit associated, the amount should already be in the normalizing unit
        if (_units == null)
            return getAmount();

        if (_units.isCompatible(_normalizingUnits))
            return _units.convertAmount(getAmount(), _normalizingUnits);
        else
            return getAmount();
    }

    public Unit getNormalizedUnits()
    {
        return _normalizingUnits != null ? _normalizingUnits : _units;
    }

    public Unit getUnits()
    {
        return _units;
    }

    public void setUnits(Unit units)
    {
        _units = units;
    }

    public void setUnits(String unitsStr)
    {
        _units = Unit.getUnit(unitsStr);
    }

    public Double getAmount()
    {
        return _amount;
    }

    public void setAmount(Double amount)
    {
        _amount = amount;
    }

    public Unit getNormalizingUnits()
    {
        return _normalizingUnits;
    }

    public void setNormalizingUnits(Unit normalizingUnits)
    {
        _normalizingUnits = normalizingUnits;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof Measurement other))
            return false;
        try
        {
            Double thisNormalized = this.getNormalizedAmount();
            Double otherNormalized = other.getNormalizedAmount();
            if (thisNormalized == null)
            {
                return otherNormalized == null;
            }
            else
            {
                return thisNormalized.equals(otherNormalized);
            }
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
    }

    @Override
    public String toString()
    {
        if (getUnits() == null)
            return String.valueOf(getAmount());

        return String.format("%f %s", getAmount(), getUnits());
    }

    public String toNormalizedString()
    {
        return String.format("%f %s", getNormalizedAmount(), getNormalizedUnits());
    }

    public static Double convertToAmount(Object amountObj)
    {
        if (amountObj == null)
            return null;
        else if (amountObj instanceof Integer)
        {
            return Double.valueOf((int) amountObj);
        }
        else if (amountObj instanceof Double)
        {
            return (Double) amountObj;
        }
        else if (amountObj instanceof String)
            try
            {
                return Double.valueOf((String) amountObj);
            }
            catch (NumberFormatException e)
            {
                throw new ConversionExceptionWithMessage("Amount (" + amountObj + ") must be a number.");
            }
        else
            throw new ConversionExceptionWithMessage("Amount (" + amountObj + ") must be a number.");
    }

    public static String getUnits(String rawUnits, Unit defaultUnits)
    {
        if (!StringUtils.isEmpty(rawUnits))
        {
            rawUnits = rawUnits.trim();
            try
            {
                Unit mUnit = Unit.valueOf(rawUnits);
                if (defaultUnits != null && mUnit.getKind() != defaultUnits.getKind())
                    throw new ConversionExceptionWithMessage("Units value (" + rawUnits + ") cannot be converted to the default units (" + defaultUnits + ").");
                return rawUnits;
            }
            catch (IllegalArgumentException e)
            {
                Unit unit = Unit.getUnitFromLabel(rawUnits);
                if (unit == null)
                    throw new ConversionExceptionWithMessage("Unsupported Units value (" + rawUnits + ").  Supported values are: " + StringUtils.join(Unit.values(), ", ") + ".");
                else
                    return unit.toString();
            }
        }

        return defaultUnits == null ? null : defaultUnits.name();
    }

}
