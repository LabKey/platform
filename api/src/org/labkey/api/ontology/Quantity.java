package org.labkey.api.ontology;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.text.Format;
import java.util.regex.Pattern;

import static org.labkey.api.ontology.Unit.g;

/* CONSIDER: it's tempting to store BigDecimal in memory after parse for math/conversion purposed, even if we store as double in the database */

/*
 *  There is a design decision here
 *      "value" is always relative to the Kind.getDefaultUnit()
 * or
 *      "value" is relative to a stored/associated unit
 *
 * I think there is less room for error if Quantity is always the stored/database value, but
 * this means that formatting code needs to be careful.
 *
 * There is also value in mimic-ing LabKey SQL semantics.  I think there is value in NOT
 * translating the "number". E.g. always have "Volume" typed data in the same scale, and always having Mass
 * typed data in the same scale.  Then units become a parse/display thing.
 *
 * LabKey SQL ideas
 *
 * CAST takes a bare number and creates a quantity type.  Warns when converting a Quantity to another kind of Quantity
 *
 * 1.23                         // untyped
 * SET CAST(1.23 AS Mass)       // 1.23g -- Convert to a Mass quantity with default unit g, display unit is 'g'
 * CAST(1.23 AS Mass('g'));     // 1.23g -- same as above, but 'g' is explicit
 * CAST(1.23 AS Mass('kg'));    // 1230g -- value is converted to g, display unit is 'kg'
 *
 * Since value is always stored in the same unit, there are no converting arithmetic methods/operators.
 * LabKey SQL does not unit arithmetic. It will preserve the kind of quantity for simple calculations
 *
 * SUM(Mass) -> Mass
 * Mass plus/minus Mass -> Mass
 * Mass times/divide number -> Mass
 * Mass/Volume = Double
 *
 * If you want to display a column in kg, don't do this "SELECT weight/1000".  That will create a new small Mass quantity
 * e.g. 7123g/1000 -> 7.123g.
 *
 *  SELECT weight as weight_in_kg @unit=kg,
 * or
 *  SELECT (CAST weight as DOUBLE)/1000.0 as weight_in_kg
 *
 */
public class Quantity extends Number implements Comparable<Quantity>
{
    public final @NotNull KindOfQuantity kind;
    public final Number value;
    private final boolean isDouble;

    private Quantity()
    {
        throw new IllegalStateException();
    }

    /* Returns a quantity = value*units
     *    of(1, Kg) -> 1000g
     */
    public static Quantity of(Number value, Unit unit)
    {
        if (value instanceof Quantity q)
        {
            if (unit.kindOfQuantity != q.kind)
                throw new ConversionException("Cannot convert " + q.format() + " to " + unit);
            return q;
        }
        if (value instanceof BigDecimal bd)
            return new Quantity(unit.kindOfQuantity, bd, unit);
        if (value instanceof Long l && (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE))
            return new Quantity(unit.kindOfQuantity, BigDecimal.valueOf(l), unit);
        return new Quantity(unit.kindOfQuantity, value.doubleValue(), unit);
    }

    protected Quantity(@NotNull KindOfQuantity kind, BigDecimal value)
    {
        this.kind = kind;
        this.value = value;
        this.isDouble = false;
    }

    protected Quantity(@NotNull KindOfQuantity kind, BigDecimal value, Unit from)
    {
        this.kind = kind;
        this.value = from.toStorageUnitValue(value);
        this.isDouble = this.value instanceof Double;
        assert isDouble || this.value instanceof BigDecimal;
    }

    protected Quantity(@NotNull KindOfQuantity kind, Double value)
    {
        this.kind = kind;
        this.value = value;
        this.isDouble = true;
    }

    protected Quantity(@NotNull KindOfQuantity kind, Double value, Unit from)
    {
        this.kind = kind;
        this.value = from.toStorageUnitValue(value);
        this.isDouble = this.value instanceof Double;
        assert isDouble || this.value instanceof BigDecimal;
    }

    public String format()
    {
        return format(kind.getDefaultDisplayUnit());
    }

    public String format(Unit unit)
    {
        return unit.fromStorageUnitValue(value) + unit.print;
    }

    public String format(Format format)
    {
        return format(kind.getDefaultDisplayUnit(), format);
    }

    public String format(Unit unit, Format format)
    {
        return format.format(unit.fromStorageUnitValue(value)) + unit.print;
    }

    @Override
    public String toString()
    {
        return kind.getName() + ":" + format(kind.getStorageUnit());
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof Quantity other && 0 == compareTo(other);
    }

    @Override
    public int compareTo(@NotNull Quantity other)
    {
        if (this.kind != other.kind)
            throw new IllegalArgumentException("Can't compare " + this.kind + " and " + other.kind);
        if (this.isDouble == other.isDouble)
            return ((Comparable)this.value).compareTo(other.value);
        BigDecimal thisDec  = this.isDouble  ? BigDecimal.valueOf((Double) this.value)  : (BigDecimal)this.value;
        BigDecimal otherDec = other.isDouble ? BigDecimal.valueOf((Double) other.value) : (BigDecimal)other.value;
        return thisDec.compareTo(otherDec);
    }

    public Number value()
    {
        return value;
    }

    public Number value(Unit unit)
    {
        return unit.fromStorageUnitValue(value);
    }

    /* Quantity implement Number, so most can can just pass this along without knowing. */
    @Override
    public int intValue()
    {
        return value.intValue();
    }

    @Override
    public long longValue()
    {
        return value.longValue();
    }

    @Override
    public float floatValue()
    {
        return value.floatValue();
    }

    @Override
    public double doubleValue()
    {
        return value.doubleValue();
    }


    /*** PARSE ***/

    private static final String NUMBER_REGEX = "(?<number>[+\\-]?(?<digits>\\d*([.]\\d*)?)(?:[Ee][+\\-]?\\d+)?)";

    // UCUM units can be pretty complicated, but we only use a small subset for now
    private static final String SIMPLE_UNIT_REGEX = "(?<unit>[a-zA-Zμℓ]+)";
    // private static final String COMPLEX_UNIT ="(?<unit>[\\[/a-zA-Z°μℓ][\\[\\]_./a-zA-Z0-9°μℓ])?"; // unit contains []_./azAZ09°μ

//    private static final Pattern pattern = Pattern.compile(NUMBER);
    private static final Pattern pattern = Pattern.compile("^" + NUMBER_REGEX + "\\s*" + SIMPLE_UNIT_REGEX + "?$");


    private static Quantity parse(@NotNull String s) throws ConversionException
    {
        return parse(s, Unit.no_unit);
    }


    /** The defaultUnit has two purposes 1) define the expected KindOfQuantity 2) select a Unit if it is not explicit in the source */
    private static Quantity parse(@NotNull String s, @NotNull Unit defaultUnit) throws ConversionException
    {
        // We could probably create a real lexer/parser here, but we only need to be able to parse units we support
        // FIRST, check if there is a space
        s = s.trim();
        var split = s.indexOf(' ');
        String valuePart=null;
        String unitPart=null;
        if (split > 0)
        {
            valuePart = s.substring(0, split);
            unitPart = s.substring(split+1);
        }
        else
        {
            var m = pattern.matcher(s);
            if (m.matches())
            {
                String digits = m.group("digits");
                // there should be digits before or after the "." (this check avoids needing a more complicated regex)
                if (!".".equals(digits) && !"".equals(digits))
                {
                    valuePart = m.group("number");
                    if (m.namedGroups().containsKey("unit"))
                        unitPart = m.group("unit");
                }
            }
        }

        if (StringUtils.isBlank(valuePart))
            throw new ConversionException("Could not parse number");

        try
        {
            Number value = StringUtils.containsAny(valuePart,"eE") ?
                    Double.valueOf(valuePart) :
                    new BigDecimal(valuePart);
            var unit = StringUtils.isBlank(unitPart) ? defaultUnit : Unit.fromName(unitPart);
            if (null == unit)
                unit = Unit.no_unit;
            if (defaultUnit!=Unit.no_unit && !defaultUnit.kindOfQuantity.accept(unit))
                throw new ConversionException("Quantity is of wrong type: expected " + defaultUnit.kindOfQuantity.getName() + " found " + unit);
            return Quantity.of(value, unit);
        }
        catch (IllegalArgumentException x)
        {
            throw new ConversionException("could not parse", x);
        }
    }


    /*** CONVERSION **/

    public abstract static class Mass_pg extends Quantity {}
    public abstract static class Mass_ng extends Quantity {}
    public abstract static class Mass_ug extends Quantity {}
    public abstract static class Mass_mg extends Quantity {}
    public abstract static class Mass_g extends Quantity {}
    public abstract static class Mass_kg extends Quantity {}
    public abstract static class Mass_Mg extends Quantity {}

    public abstract static class Volume_pl extends Quantity {}
    public abstract static class Volume_nl extends Quantity {}
    public abstract static class Volume_ul extends Quantity {}
    public abstract static class Volume_ml extends Quantity {}
    public abstract static class Volume_l extends Quantity {}
    public abstract static class Volume_kl extends Quantity {}
    public abstract static class Volume_Ml extends Quantity {}

    public abstract static class Volume extends Quantity
    {
    }


    // convert (ala BeanUtils.Converter to Quantity
    public static Quantity convert(Object o, Unit unit)
    {
        if (null == o)
            return null;
        if (o instanceof Quantity q)
        {
            if (q.kind == unit.kindOfQuantity)
                return q;
            throw new ConversionException("Cannot convert " + q.format() + " to " + unit);
        }
        if (o instanceof Number n)
        {
            return Quantity.of(n, unit);
        }
        return Quantity.parse(String.valueOf(o), unit);
    }


    public static Converter converterFor(Unit unit)
    {
        return new Converter()
        {
            @Override
            public <T> T convert(Class<T> aClass, Object o)
            {
                return (T)Quantity.convert(o, unit);
            }
        };
    }

    static void registerQuantityConverters()
    {
        for (Unit unit : Unit.values())
            ConvertUtils.register(converterFor(unit), unit.getQuantityClass());
    }

    static
    {
        registerQuantityConverters();
    }


    /*** TEST ***/

    public static class TestCase extends Assert
    {
        void failToParse(String s)
        {
            try
            {
                parse(s);
                fail("expected exception for " + s);
            }
            catch (ConversionException x)
            {
                // YEAH
            }
        }

        void failToParse(String s, Unit defaultUnit)
        {
            try
            {
                parse(s, defaultUnit);
                fail("expected exception for " + s);
            }
            catch (ConversionException x)
            {
                // YEAH
            }
        }

        @Test
        public void testParse()
        {
            assertEquals(Quantity.of(1,g), parse("1", g));
            assertEquals(Quantity.of(1,g), parse("1g"));
            assertEquals(Quantity.of(1,g), parse("0.001kg"));
            assertEquals(parse("1000mg"), parse("0.001kg"));

            failToParse("kg");
            failToParse("124xyz");
            failToParse("g100");

            failToParse("1g", Unit.l);
        }

        @Test
        public void testPattern()
        {
            // no exponents
            assertEquals(0.1, Double.valueOf(".1"), 0.0);
            assertEquals(0.1, Double.valueOf("+.1"), 0.0);
            assertEquals(-0.1, Double.valueOf("-.1"), 0.0);
            assertTrue(pattern.matcher("1").matches());
            assertTrue(pattern.matcher("+1").matches());
            assertTrue(pattern.matcher("-1").matches());
            assertTrue(pattern.matcher("1.").matches());
            assertTrue(pattern.matcher("+1.").matches());
            assertTrue(pattern.matcher("-1.").matches());
            assertTrue(pattern.matcher(".2").matches());
            assertTrue(pattern.matcher("+.2").matches());
            assertTrue(pattern.matcher("-.2").matches());

            // no digits
            assertFalse(pattern.matcher("+").matches());
            assertFalse(pattern.matcher(".").matches());
            assertFalse(pattern.matcher("-").matches());
            assertFalse(pattern.matcher("+e1").matches());

            //exponents
            assertEquals(0.1e2, Double.valueOf(".1e2"), 0.0);
            assertTrue(pattern.matcher(".1e2").matches());
            assertTrue(pattern.matcher("1.23e4").matches());
            assertTrue(pattern.matcher("1.23E4").matches());
            assertTrue(pattern.matcher("+1.23E4").matches());
            assertTrue(pattern.matcher("-1.23E-4").matches());

            // units
            assertTrue(pattern.matcher("1.23μF").matches());
            assertTrue(pattern.matcher("1.2e3 mℓ").matches());
        }


        @Test
        public void testConversion()
        {
            Quantity q;
            registerQuantityConverters();

            q = (Quantity)ConvertUtils.convert("1.234kg", Quantity.Mass_g.class);
            assertEquals(q.getClass(), Quantity.class);
            assertEquals(new Quantity(KindOfQuantity.Mass, 1234d), q);

            q = (Quantity)ConvertUtils.convert("1234", Quantity.Mass_g.class);
            assertEquals(q.getClass(), Quantity.class);
            assertEquals(new Quantity(KindOfQuantity.Mass, 1234d), q);
        }
    }
}
