package org.labkey.api.data.measurement;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConversionExceptionWithMessage;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Measurement
{
    private static final String NUMBER_REGEX = "[+\\-]?\\d+(?:\\.\\d+)?(?:[Ee][+\\-]\\d+)?";
    private static final Pattern AMOUNT_AND_UNITS_PATTERN = Pattern.compile("\\s*(?<amount>" + NUMBER_REGEX + ")?\\s*(?<units>\\S*)\\s*");
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
        uL("microliters", Kind.Volume, 0.001, "μL"),
        L("liters", Kind.Volume, 1000),
        kL("kiloliters", Kind.Volume, 100000),
        unit("units", Kind.Count, 1);

        private final String _longLabel;
        private final Kind _kind;
        private final double _ratio;
        private final String _alternateName;

        Unit(String longLabel, Kind kind, double ratio)
        {
           this(longLabel, kind, ratio, null);
        }

        Unit(String longLabel, Kind kind, double ratio, String alternateName)
        {
            _longLabel = longLabel;
            _kind = kind;
            _ratio = ratio;
            _alternateName = alternateName;
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

        public String getAlternateName()
        {
            return _alternateName;
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
                if (name.equalsIgnoreCase(unit.name()) || name.equalsIgnoreCase(unit.getAlternateName()))
                    return unit;
            }
            return null;
        }

        public Double convertAmountForDisplay(@Nullable Double amount, @Nullable Measurement.Unit targetUnit)
        {
            Double converted = convertAmount(amount, targetUnit);
            if (converted == null)
                return null;

            return Precision.round(converted, 6);
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
            if (StringUtils.isBlank(stringA) && StringUtils.isBlank(stringB))
                return true;

            if (StringUtils.isBlank(stringA) || StringUtils.isBlank(stringB))
                return false;

            if (stringA.equals(stringB))
                return true;

            Unit unitA = getUnitFromName(stringA);
            Unit unitB = getUnitFromName(stringB);

            if (unitA == null ||unitB == null)
                return false;

            return unitA.getKind() == unitB.getKind();
        }

        public static class TestCase extends Assert
        {
            @Test
            public void testIsCompatibleString()
            {
                assertTrue("empty string should be compatible with null", Unit.isCompatible(" ", null));
                assertTrue("empty string should be compatible with null", Unit.isCompatible(null, ""));
                assertTrue("null strings should be compatible", Unit.isCompatible(null, null));
                assertTrue("empty string should be compatible with other empty string", Unit.isCompatible(" ", "     "));
                assertFalse("empty string should be not be compatible with non-empty string", Unit.isCompatible("mg", "     "));
                assertTrue("same string should be compatible", Unit.isCompatible("mL", "mL"));
                assertTrue("units with same base should be compatible", Unit.isCompatible("mL", "L"));
                assertTrue("units with same base should be compatible", Unit.isCompatible("kG", "g"));
                assertFalse("units with different base should not be compatible", Unit.isCompatible("mg", "units"));
                assertFalse("invalid unit should not be compatible with valid unit", Unit.isCompatible("xL", "mL"));
                assertFalse("invalid units should not be compatible", Unit.isCompatible("xL", "tsp"));
            }

            @Test
            public void testConvertAmount()
            {
                assertEquals("convert to same unit", (Double) 1.0, Unit.g.convertAmount(1.0, Unit.g));
                assertEquals("convert to larger unit", (Double) 0.001, Unit.g.convertAmount(1.0, Unit.kg));
                assertEquals("convert to smaller unit", (Double) 1000.0, Unit.g.convertAmount(1.0, Unit.mg));
                assertNull("convert null amount", Unit.mL.convertAmount(null, Unit.L));
                try {
                    Unit.g.convertAmount(2.0, Unit.kL);
                    fail("Conversion to incompatible unit should throw exception.");
                } catch (IllegalArgumentException ignore)
                {
                    // do nothing
                }
            }

            @Test
            public void testGetUnit()
            {
                assertNull("invalid unit label should return null", getUnit("invalid"));
                assertNull("invalid unit string should return null", getUnit("inv"));
                assertEquals("valid unit should return object", Unit.g, getUnit("g"));
                assertEquals("valid unit should return object", Unit.g, getUnit("grams"));
            }
        }
    }

    public Measurement(Object amountObj, String units, @Nullable String normalizingUnits) throws ConversionExceptionWithMessage
    {
        _amount = convertToAmount(amountObj);
        _normalizingUnits = Unit.getUnit(normalizingUnits);
        validateUnits(units, _normalizingUnits);
        _units = Unit.getUnit(units);
    }

    public Measurement(Object amountObj, String units)
    {
       this(amountObj, units, null);
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
        else if (amountObj instanceof BigDecimal)
        {
            return ((BigDecimal) amountObj).doubleValue();
        }
        else if (amountObj instanceof String)
            try
            {
                if (StringUtils.isBlank((String) amountObj))
                    return null;
                return Double.valueOf((String) amountObj);
            }
            catch (NumberFormatException e)
            {
                throw new ConversionExceptionWithMessage("Amount (" + amountObj + ") must be a number.");
            }
        else
            throw new ConversionExceptionWithMessage("Amount (" + amountObj + ") must be a number.");
    }

    public static Measurement parse(String stringValue)
    {
        if (StringUtils.isBlank(stringValue))
            return null;
        Matcher matcher = AMOUNT_AND_UNITS_PATTERN.matcher(stringValue);
        if (!matcher.matches())
            return null;
        return new Measurement(convertToAmount(matcher.group("amount")), matcher.group("units"));
    }

    public static void validateUnits(String rawUnits, Unit defaultUnits)
    {
        if (!StringUtils.isBlank(rawUnits))
        {
            rawUnits = rawUnits.trim();

            Unit mUnit = Unit.getUnitFromName(rawUnits);
            if (mUnit == null)
            {
                mUnit = Unit.getUnitFromLabel(rawUnits);
                if (mUnit == null)
                    throw new ConversionExceptionWithMessage("Unsupported Units value (" + rawUnits + ").  Supported values are: " + StringUtils.join(Unit.values(), ", ") + ".");
            }
            if (defaultUnits != null && mUnit.getKind() != defaultUnits.getKind())
                throw new ConversionExceptionWithMessage("Units value (" + rawUnits + ") cannot be converted to the default units (" + defaultUnits + ").");
        }
    }

    public static class TestCase extends Assert
    {

        @Test
        public void testValidateUnits()
        {
            try
            {
                Measurement.validateUnits("g", Unit.mg);
                Measurement.validateUnits("g ", Unit.mg);
                Measurement.validateUnits(" g ", Unit.mg);
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("Compatible unit should not throw exception.");
            }
            try
            {
                Measurement.validateUnits(null, Unit.unit);
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("null units should validate");
            }
            try
            {
                Measurement.validateUnits("", Unit.unit);
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("empty units should validate");
            }
            try
            {
                Measurement.validateUnits("g", Unit.unit);
                fail("Units that are not comparable should throw exception.");
            }
            catch (ConversionExceptionWithMessage ignore)
            {

            }

            try
            {
                Measurement.validateUnits("nonesuch", Unit.unit);
                fail("Invalid units should throw exception.");
            }
            catch (ConversionExceptionWithMessage ignore)
            {

            }

        }

        @Test
        public void testConvertToAmount()
        {
            assertNull("null value should convert to null", Measurement.convertToAmount(null));
            assertNull("empty string conversion",  Measurement.convertToAmount(""));
            assertNull("blank string conversion",  Measurement.convertToAmount("   "));
            assertEquals("string conversion", (Double) 32.0, Measurement.convertToAmount("32.0"));
            assertEquals("integer conversion", (Double) 18.0, Measurement.convertToAmount(18));
            assertEquals("double conversion", (Double) 819.2, Measurement.convertToAmount(819.2));
            try
            {
                Measurement.convertToAmount("not-a-number");
                fail("conversion of non-number should throw exception");
            }
            catch (ConversionExceptionWithMessage ignore)
            {

            }
        }

        @Test
        public void testEquals()
        {
            Measurement measurement = new Measurement("43.2", "g", "milligrams");
            assertNotEquals("non-unit object", "43.2 g", measurement);
            assertNotEquals("different amounts",  measurement, new Measurement("23.4", "g", "milligrams"));
            assertEquals("same units", measurement, new Measurement("43.2", "g", "mg"));
            assertEquals("different units", measurement, new Measurement("43200", "mg", "mg"));
            assertEquals("no normalizing unit", measurement, new Measurement("43200", "mg", null));
            assertEquals("case-insensitive", measurement, new Measurement("43200", "MilliGRAMS", null));
        }

        @Test
        public void testParse()
        {
            assertNull("Empty string should result in null object", Measurement.parse(""));
            assertNull("Null should result in null object", Measurement.parse(null));
            assertNull("Blank string should result in null object", Measurement.parse(" "));
            assertNull("Parse with multiple-word units failed", Measurement.parse("878.8 micro liters"));

            try
            {
                Measurement.parse("71.9141x");
                fail("Unsupported unit type 'x' not detected");
            }
            catch (ConversionExceptionWithMessage ignore)
            {
                // expected
            }
            try
            {
                Measurement.parse("cups");
                fail("Unsupported unit type 'cups' not detected");
            }
            catch (ConversionExceptionWithMessage ignore)
            {
                // expected
            }
            assertEquals("String with just a number did not parse", new Measurement(43.2431, null), Measurement.parse("43.2431"));
            assertEquals("String with just units did not parse", new Measurement(null, "grams"), Measurement.parse("g"));
            assertEquals("String with number and units did not parse", new Measurement(43.2, "g"), Measurement.parse("43.2 g"));
            assertEquals("String with number and units without a space between did not parse", new Measurement(43.2, "g"), Measurement.parse("43.2g"));
            assertEquals("String with number and units without a space between did not parse", new Measurement(43.2, "g"), Measurement.parse("43.2grams"));
            assertEquals("String with integer and units did not parse", new Measurement(42, "g"), Measurement.parse("42 g"));
            assertEquals("String with space padding before number did not parse", new Measurement(43.2, "g"), Measurement.parse(" 43.2 g"));
            assertEquals("String with full unit name did not parse", new Measurement(43.2, "g"), Measurement.parse("43.2 grams"));
            assertEquals("String with full unit name and padding did not parse", new Measurement(43.2, "mg"), Measurement.parse("  43.2    milligrams   "));
            assertEquals("String with complex number did not parse", new Measurement(-189140.0, "g"), Measurement.parse("-18.914e+4 g"));
            assertEquals("String with leading plus did not parse", new Measurement(183.0, "mL"), Measurement.parse("+183 mL"));
            assertEquals("Other complex number did not parse", new Measurement(1.83431, "mL"), Measurement.parse("+183431E-5 units"));
            assertEquals("Parse of units not matching case failed", new Measurement(878.8, "g"), Measurement.parse("878.8 G"));
            assertEquals("Parse of full unit name not matching case failed", new Measurement(878.8, "g"), Measurement.parse("878.8 GRaMS"));
            assertEquals("Parse with alternate name failed", new Measurement(878.8, "uL"), Measurement.parse("878.8 μL"));
        }
    }


}
