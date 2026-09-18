/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.ontology;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.util.Formats;
import org.labkey.api.util.logging.LogHelper;

import java.math.BigDecimal;
import java.text.Format;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
    public static final Logger LOG = LogHelper.getLogger(Quantity.class, "Quantity operations");
    public static final int DEFAULT_PRECISION_SCALE = 6;
    public static final String DEFAULT_FORMAT = "0.######";
    public final @NotNull KindOfQuantity kind;
    public final Number value;
    private final boolean isDouble;

    private Quantity()
    {
        throw new IllegalStateException();
    }

    @Nullable
    public static Quantity of(@Nullable Object value, @Nullable String unitsStr)
    {
        LOG.debug("Getting quantity of {} for unitsStr {}", value, unitsStr);
        if (value == null)
            return null;
        if (!(value instanceof Number))
            throw new IllegalArgumentException("Value must be a number");
        if (unitsStr != null)
            return Quantity.of((Number) value, unitsStr);
        else
            return Quantity.of((Number) value, Unit.unit);
    }

    @NotNull
    public static Quantity of(@NotNull Number value, @NotNull String unitStr)
    {
        Unit unit = Unit.fromName(unitStr);
        if (unit == null)
            throw new IllegalArgumentException("Unknown unit (" + unitStr + ") for quantity " + value + ".");
        return of(value, unit);
    }

    /* Returns a quantity = value*units
     *    of(1, Kg) -> 1000g
     */
    @NotNull
    public static Quantity of(@NotNull Number value, @NotNull Unit unit)
    {
        LOG.debug("Creating quantity of {} for unit {}", value, unit);
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
        LOG.debug("Creating new Quantity of kind {}, double {}, unit {}", unit.kindOfQuantity, value, unit);
        return new Quantity(unit.kindOfQuantity, value.doubleValue(), unit);
    }

    protected Quantity(@NotNull KindOfQuantity kind, BigDecimal value)
    {
        this.kind = kind;
        this.value = value;
        this.isDouble = false;
    }

    protected Quantity(@NotNull KindOfQuantity kind, BigDecimal value, @NotNull Unit from)
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

    protected Quantity(@NotNull KindOfQuantity kind, Double value, @NotNull Unit from)
    {
        LOG.debug("Quantity constructor with kind {}, double {}, unit {}", kind, value, from);
        this.kind = kind;
        this.value = from.toStorageUnitValue(value);
        this.isDouble = this.value instanceof Double;
        assert isDouble || this.value instanceof BigDecimal;
    }

    public @NotNull KindOfQuantity getKind()
    {
        return kind;
    }

    public Quantity add(Quantity delta)
    {
        if (delta.kind != kind)
            throw new ConversionException("Cannot add " + delta + " to " + this + ".");

        return new Quantity(this.kind, this.doubleValue() + delta.doubleValue());
    }

    public double doubleValue(@NotNull Unit unit)
    {
        if (unit == kind.getStorageUnit())
            return value.doubleValue();
        else
            return Unit.convert(value.doubleValue(), kind.getStorageUnit(), unit);
    }

    public String format()
    {
        return format(kind.getDefaultDisplayUnit());
    }

    public String format(@NotNull Unit unit)
    {
        return value(unit) + unit.print;
    }

    public String format(Format format)
    {
        return format(kind.getDefaultDisplayUnit(), format);
    }

    public String format(@NotNull Unit unit, Format format)
    {
        return format.format(value(unit)) + unit.print;
    }

    /**
     * Quantity extends Numeric so we kinda expect .toString() be a number.
     * However, it's really important that quantity == ConvertUtils.convert(quantity.toString(), Quantity.class).
     */
    @Override
    public String toString()
    {
        var ret = format(kind.getStorageUnit());
        // FIXME currently this call to ConvertUtils.convert does not behave as expected.
        // When ret is something like "10mL", it is using a converter for Unit.unit
        // (The theory is it's either the first or last one that was registered).
//        assert this == ConvertUtils.convert(ret, this.getClass());
        return ret;
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof Quantity other && this.kind == other.kind && 0 == compareTo(other);
    }

    /* compareTo() treats equal values as equal regardless of BigDecimal scale or Double/BigDecimal representation, so hash on the common double value */
    @Override
    public int hashCode()
    {
        return Objects.hash(kind, value.doubleValue());
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

    public Number value(@NotNull Unit unit)
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

    private static final Pattern pattern = Pattern.compile("^" + NUMBER_REGEX + "\\s*" + SIMPLE_UNIT_REGEX + "?$");


    private static Quantity parse(@NotNull String s) throws ConversionException
    {
        LOG.debug("Parsing quantity {} as Unit.unit", s);
        return parse(s, Unit.unit);
    }


    /** The defaultUnit has two purposes 1) define the expected KindOfQuantity 2) select a Unit if it is not explicit in the source */
    private static Quantity parse(@NotNull String s, @NotNull Unit defaultUnit) throws ConversionException
    {
        LOG.debug("Parsing quantity of {} with default unit {}", s, defaultUnit);
        // We could probably create a real lexer/parser here, but we only need to be able to parse units we support
        // FIRST, check if there is a space
        s = s.trim();
        var split = s.indexOf(' ');
        String valuePart=null;
        String unitPart=null;
        if (split > 0)
        {
            valuePart = s.substring(0, split).trim();
            unitPart = s.substring(split+1).trim();
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
                    if (StringUtils.isNotBlank(m.group("unit"))) //m.namedGroups().containsKey("unit"))
                        unitPart = m.group("unit");
                }
            }
        }

        if (StringUtils.isBlank(valuePart))
            throw new ConversionException("Could not parse number from '" + s + "'.");

        try
        {
            Number value = StringUtils.containsAny(valuePart,"eE") ?
                    Double.valueOf(valuePart) :
                    new BigDecimal(valuePart);

            var unit = StringUtils.isBlank(unitPart) ? defaultUnit : Unit.fromName(unitPart);
            if (null == unit)
                throw new ConversionException("Could not parse unit '" + unitPart + "' from '" + s + "'.");
            if (!defaultUnit.kindOfQuantity.accept(unit))
                throw new ConversionException("Quantity for value " + value + " is of the wrong type. Expected " + defaultUnit.kindOfQuantity.getName() + ", but found " + unit);
            return Quantity.of(value, unit);
        }
        catch (IllegalArgumentException x)
        {
            throw new ConversionException("Could not parse quantity from '" + s + "'.", x);
        }
    }


    /*** CONVERSION **/

    public abstract static class Mass_pg extends Quantity {}
    public abstract static class Mass_ng extends Quantity {}
    public abstract static class Mass_ug extends Quantity {}
    public abstract static class Mass_mg extends Quantity {}
    public abstract static class Mass_g extends Quantity {}
    public abstract static class Mass_kg extends Quantity {}
    public abstract static class Mass_Megag extends Quantity {}

    public abstract static class Volume_pl extends Quantity {}
    public abstract static class Volume_nl extends Quantity {}
    public abstract static class Volume_ul extends Quantity {}
    public abstract static class Volume_ml extends Quantity {}
    public abstract static class Volume_l extends Quantity {}
    public abstract static class Volume_kl extends Quantity {}
    public abstract static class Volume_Megal extends Quantity {}

    public abstract static class Volume extends Quantity
    {
    }


    // convert (ala BeanUtils.Converter to Quantity
    public static Quantity convert(@Nullable Object o, Unit unit)
    {
        LOG.debug("Converting quantity {} to unit {}", o, unit);
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
                LOG.debug("In convertedFor.convert with object {} and unit {}", o, unit);
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

        @BeforeClass
        public static void setup()
        {
            registerQuantityConverters();
        }

        @Test
        public void testAdd()
        {
            Quantity starting = Quantity.of(1024, Unit.mg);
            assertEquals(starting, starting.add(Quantity.of(0, Unit.mg)));
            assertEquals(Quantity.of(1034, Unit.mg), starting.add(Quantity.of(10, Unit.mg)));
            assertEquals(Quantity.of(1.022, Unit.g), starting.add(Quantity.of(-2, Unit.mg)));
            assertEquals(Quantity.of(33024, Unit.mg), starting.add(Quantity.of(32, Unit.g)));
            assertEquals(Quantity.of(1.024200, Unit.g), starting.add(Quantity.of(200, Unit.ug)));
            assertEquals(Quantity.of(1023800, Unit.ug), starting.add(Quantity.of(-200, Unit.ug)));
            try
            {
                starting.add(Quantity.of(10, "mL"));
                fail("Adding quantities of different kinds should throw an error.");
            }
            catch (ConversionException x)
            {
                assertEquals("Cannot add 10.0mL to 1.024g.", x.getMessage());
            }
        }

        @Test
        public void testParse()
        {
            assertEquals(Quantity.of(1, Unit.g), parse("1", Unit.g));
            assertEquals(Quantity.of(1, Unit.g), parse("1g", Unit.g));
            assertEquals(Quantity.of(1, Unit.g), parse("0.001kg", Unit.g));

            assertEquals(Quantity.of(1, Unit.count), parse("1"));
            assertEquals(Quantity.of(1, Unit.count), parse("1 unit"));
            assertEquals(Quantity.of(0, Unit.count), parse("0 units"));
            assertEquals(Quantity.of(0, Unit.count), parse("0count"));

            assertEquals(Quantity.of(1, Unit.count), parse("1", Unit.boxes));
            assertEquals(Quantity.of(1, Unit.unit), parse("1", Unit.blocks));
            assertEquals(Quantity.of(1, Unit.cells), parse("1", Unit.tests));

            assertEquals(parse("1000mg", Unit.g), parse("0.001kg", Unit.g));
            assertEquals(parse(" 1000mg", Unit.g), parse("0.001kg", Unit.g));
            assertEquals(parse("1000mg ", Unit.g), parse("0.001kg", Unit.g));
            assertEquals(parse("1000 mg", Unit.g), parse("0.001kg", Unit.g));
            assertEquals(parse("1000  mg", Unit.g), parse("0.001kg", Unit.g));
        }

        @Test
        public void testFailToParseNoDigit()
        {
            failToParse("kg");
            failToParse("test");
            failToParse("+");
            failToParse(".");
            failToParse("-");
            failToParse("+e1");
        }

        @Test
        public void testFailToParseInvalidUnit()
        {
            failToParse("124xyz");
            failToParse("124 xyz");
            failToParse("g100");
        }

        @Test
        public void testFailToParseCantConvert()
        {
            failToParse("1g", Unit.L);
            failToParse("1g", Unit.mL);
            failToParse("1g", Unit.count);
            failToParse("1g", Unit.unit);
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
            assertTrue(pattern.matcher("+").matches());
            assertTrue(pattern.matcher(".").matches());
            assertTrue(pattern.matcher("-").matches());
            assertTrue(pattern.matcher("+e1").matches());

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
            assertTrue(pattern.matcher("test").matches());
            assertTrue(pattern.matcher("g").matches());
            assertTrue(pattern.matcher("mℓ").matches());
        }


        @Test
        public void testConversion()
        {
            Quantity q;

            q = (Quantity)ConvertUtils.convert("1.234kg", Quantity.Mass_g.class);
            assertEquals(Quantity.class, q.getClass());
            assertEquals(new Quantity(KindOfQuantity.Mass, 1234d), q);

            q = (Quantity)ConvertUtils.convert("1234", Quantity.Mass_g.class);
            assertEquals(Quantity.class, q.getClass());
            assertEquals(new Quantity(KindOfQuantity.Mass, 1234d), q);

            q = (Quantity)ConvertUtils.convert("1234", Quantity.Mass_kg.class);
            assertEquals(Quantity.class, q.getClass());
            assertEquals(new Quantity(KindOfQuantity.Mass, 1234000d), q);
        }

        @Test
        public void testEqualsAcrossKinds()
        {
            Quantity mass = Quantity.of(1, Unit.g);
            Quantity volume = Quantity.of(1, Unit.mL);
            Quantity count = Quantity.of(1, Unit.unit);

            assertNotEquals(mass, volume);
            assertNotEquals(volume, mass);
            assertNotEquals(mass, count);
            assertNotEquals(count, volume);

            assertNotEquals(mass, null);
            assertNotEquals(mass, 1.0);
        }

        @Test
        public void testHashCode()
        {
            Quantity oneGram = Quantity.of(1, Unit.g);
            Quantity oneThousandMilligrams = Quantity.of(1000, Unit.mg);
            Quantity oneThousandthKilogram = Quantity.of(new BigDecimal("0.001"), Unit.kg);
            Quantity oneGramAsBigDecimal = Quantity.of(new BigDecimal("1.000"), Unit.g);

            assertEquals(oneGram, oneThousandMilligrams);
            assertEquals(oneGram.hashCode(), oneThousandMilligrams.hashCode());
            assertEquals(oneGram, oneThousandthKilogram);
            assertEquals(oneGram.hashCode(), oneThousandthKilogram.hashCode());
            assertEquals(oneGram, oneGramAsBigDecimal);
            assertEquals(oneGram.hashCode(), oneGramAsBigDecimal.hashCode());

            Set<Quantity> quantities = new HashSet<>();
            quantities.add(oneGram);
            assertTrue(quantities.contains(oneThousandMilligrams));
            assertTrue(quantities.contains(oneThousandthKilogram));
            assertTrue(quantities.contains(oneGramAsBigDecimal));

            quantities.add(oneThousandMilligrams);
            quantities.add(oneThousandthKilogram);
            quantities.add(oneGramAsBigDecimal);
            assertEquals(1, quantities.size());

            quantities.add(Quantity.of(2, Unit.g));
            quantities.add(Quantity.of(1, Unit.mL));
            assertEquals(3, quantities.size());
        }

        @Test
        public void testDoubleValue()
        {
            Quantity q = Quantity.of(1, Unit.g);
            assertEquals(1.0, q.doubleValue(Unit.g), 0.0);
            assertEquals(0.001, q.doubleValue(Unit.kg), 0.0);
            assertEquals(1000.0, q.doubleValue(Unit.mg), 0.001);
            assertEquals(1000000.0, q.doubleValue(Unit.ug), 0.001);
            assertEquals(1000000000.0, q.doubleValue(Unit.ng), 0.001);
        }

        @Test
        public void testFormat()
        {
            Quantity q = Quantity.of(1, Unit.g);
            assertEquals("1.0g", q.format());
            assertEquals("1.0g", q.format(Unit.g));
            assertEquals("0.001kg", q.format(Unit.kg));
            assertEquals("1000.0mg", q.format(Unit.mg));

            // with format
            assertEquals("1.000g", q.format(Formats.f3));
            assertEquals("1.000g", q.format(Unit.g, Formats.f3));
            assertEquals("0.001kg", q.format(Unit.kg, Formats.f3));
            assertEquals("1000000ug", q.format(Unit.ug, Formats.fv3));
        }
    }
}
