package org.labkey.api.data.measurement;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

public class Measurement
{
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
                throw new ConversionException("Amount (" + amountObj + ") must be a number.");
            }
        else
            throw new ConversionException("Amount (" + amountObj + ") must be a number.");
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
                    throw new ConversionException("Units value (" + rawUnits + ") cannot be converted to the default units (" + defaultUnits + ").");
                return rawUnits;
            }
            catch (IllegalArgumentException e)
            {
                Unit unit = Unit.getUnitFromLabel(rawUnits);
                if (unit == null)
                    throw new ConversionException("Unsupported Units value (" + rawUnits + ").  Supported values are: " + StringUtils.join(Unit.values(), ", ") + ".");
                else
                    return unit.toString();
            }
        }

        return defaultUnits == null ? null : defaultUnits.name();
    }

}
