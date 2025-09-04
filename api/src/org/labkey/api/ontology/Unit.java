package org.labkey.api.ontology;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConversionExceptionWithMessage;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public enum Unit
{
    unit(KindOfQuantity.Count, null, 1.0, 2, "unit",
            Quantity.class,
            "unit", "units"),
    count(KindOfQuantity.Count, unit, 1.0, 2, "count",
            Quantity.class,
            "count", "count"),

    mL(KindOfQuantity.Volume, null, 1e0, 6, "mL",
            Quantity.Volume_ml.class,
            "milliliter", "milliliters",
            "ml", "millilitre", "millilitres"),
    // UCUM prefers "l", but "L" is also common and already supported by inventory (sorry Lambert)
    L(KindOfQuantity.Volume, mL, 1e3, 9, "L",
            Quantity.Volume_l.class,
            "liter", "liters",
            "l", "ℓ", "litre", "liters"),
    // is it better to include these little used units, to avoid future case-sensitivity problems?
    ML(KindOfQuantity.Volume, mL, 1e9, 12, "ML",
            Quantity.Volume_Megal.class,
            "megaliter", "megaliters",
            "Ml", "megalitre", "megalitres"),
    kL(KindOfQuantity.Volume, mL, 1e6, 12, "kL",
            Quantity.Volume_kl.class,
            "kiloliter", "kiloliters",
            "kl", "kilolitre", "kilolitres"),
    uL(KindOfQuantity.Volume, mL, 1e-3, 3, "uL",
            Quantity.Volume_ul.class,
            "microliter", "microliters",
            "ul", "μl", "μL", "microlitre", "microlitres"),
    nL(KindOfQuantity.Volume, mL, 1e-6, 3, "nL",
            Quantity.Volume_nl.class,
            "nanoliter", "nanoliters",
            "nl", "nanolitre", "nanolitres"),
    pL(KindOfQuantity.Volume, mL, 1e-9, 3, "pL",
            Quantity.Volume_pl.class,
            "picoliter", "picoliters",
            "pl", "picolitre", "picolitres"),

    g(KindOfQuantity.Mass, null, 1e0, 9, "g",
            Quantity.Mass_g.class,
            "gram", "grams"),
    Mg(KindOfQuantity.Mass, g, 1e6, 12, "Mg",
            Quantity.Mass_Megag.class,
            "megagram", "megagrams",
            "tonne", "tonnes"),
    kg(KindOfQuantity.Mass, g, 1e3, 12, "kg",
            Quantity.Mass_kg.class,
            "kilogram", "kilograms"),
    mg(KindOfQuantity.Mass, g, 1e-3, 6, "mg",
            Quantity.Mass_mg.class,
            "milligram", "milligrams"),
    ug(KindOfQuantity.Mass, g, 1e-6, 3, "ug",
            Quantity.Mass_ug.class,
            "microgram", "micrograms",
        "μg"),
    ng(KindOfQuantity.Mass, g, 1e-9, 3, "ng",
            Quantity.Mass_ng.class,
            "nanogram", "nanograms"),
    pg(KindOfQuantity.Mass, g, 1e-12, 3, "pg",
            Quantity.Mass_pg.class,
            "picogram", "picograms");

    private static final String CONVERSION_EXCEPTION_MESSAGE ="Units value (%s) is not compatible with the display units (%s).";

    @Getter
    final @NotNull KindOfQuantity kindOfQuantity;
    @Getter
    final @NotNull Unit base;
    final @NotNull String print;
    // this is not a 'real' class, but is used for ConvertHelper binding
    @Getter
    final @NotNull Class<? extends Quantity> quantityClass;
    final @NotNull String singular;
    final @NotNull String plural;
    final String[] otherNames;
    @Getter
    final double value;
    @Getter
    final int precisionScale;

    Unit(@NotNull KindOfQuantity kind, Unit base, double value, int precisionScale, @NotNull String printName,
         @NotNull Class<? extends Quantity> quantityClass,
         @NotNull String singular, @NotNull String plural, String... otherNames)
    {
        this.kindOfQuantity = kind;
        this.base = null == base ? this : base;
        this.value = value;
        this.precisionScale = precisionScale;
        this.print = printName;
        this.quantityClass = quantityClass;
        this.singular = singular;
        this.plural = plural;
        this.otherNames = null==otherNames || otherNames.length==0 ? null : otherNames;
    }

    public boolean isBase()
    {
        return this == base;
    }

    public boolean isCompatible(Unit other)
    {
        return other != null && other.base == base;
    }

    public double toBaseUnitValue(double v)
    {
        return v * this.value;
    }

    public double fromBaseUnitValue(double v)
    {
        return v / this.value;
    }

    public Number toStorageUnitValue(@NotNull Number v)
    {
        Quantity.LOG.info("In toStorageUnitValue");
        if (this == kindOfQuantity.getStorageUnit())
            return v;
        Quantity.LOG.info("in toStorageUnitValue with value {} ", v);
        return convert(v.doubleValue(), this, kindOfQuantity.storageUnit);
    }

    public Number fromStorageUnitValue(@NotNull Number v)
    {
        if (this == kindOfQuantity.getStorageUnit())
            return v;
        Quantity.LOG.info("in fromStorageUnitValue with value {} ", v);
        return convert(v.doubleValue(), kindOfQuantity.storageUnit, this);
    }

    @Override
    public String toString()
    {
        return print;
    }

    static final HashMap<String,Unit> unitMap = new HashMap<>(Unit.values().length*10);
    static
    {
        for (Unit unit : Unit.values())
        {
            unitMap.put(unit.name(), unit);
            unitMap.put(unit.print, unit);
            unitMap.put(unit.singular, unit);
            unitMap.put(unit.plural, unit);
            if (null != unit.otherNames)
                for (String name : unit.otherNames)
                    unitMap.put(name, unit);
        }
    }


    public static Unit fromName(@Nullable String unitName)
    {
        if (StringUtils.isEmpty(unitName))
            return null;
        Unit unit = unitMap.get(unitName);
        if (null == unit)
            unit = unitMap.get(unitName.toLowerCase());
        return unit;
    }

    // don't assume multiplicative relation between units (e.g. Kelvin and Celsius)
    static Function<Double,Double> convertFn(Unit from, Unit to)
    {
        if (from == to)
            return Function.identity();
        if (from.base != to.base)
            throw new IllegalArgumentException("Can't convert " + from.name() + " to " + to.name());
        return (x) -> to.fromBaseUnitValue(from.toBaseUnitValue(x));
    }

    public static double convert(double value, @NotNull Unit from, @NotNull Unit to)
    {
        Quantity.LOG.info("Converting value {} from {} to {}", value, from.name(), to.name());
        if (from.base != to.base)
            throw new IllegalArgumentException("Can't convert " + from.name() + " to " + to.name());
        return from==to ? value : to.fromBaseUnitValue(from.toBaseUnitValue(value));
    }

    public Quantity convert(@Nullable Object value)
    {
        return Quantity.convert(value, this);
    }

    public static Unit getValidatedUnit(@Nullable Object rawUnits, @Nullable Unit defaultUnits)
    {
        if (rawUnits == null)
            return null;
        if (rawUnits instanceof Unit u)
            if (defaultUnits == null)
                return u;
            else if (u.kindOfQuantity != defaultUnits.kindOfQuantity)
                throw new ConversionExceptionWithMessage(String.format(CONVERSION_EXCEPTION_MESSAGE, rawUnits, defaultUnits));
            else
                return u;
        if (!(rawUnits instanceof String rawUnitsString))
            throw new ConversionExceptionWithMessage(String.format(CONVERSION_EXCEPTION_MESSAGE, rawUnits, defaultUnits));
        if (!StringUtils.isBlank(rawUnitsString))
        {
            rawUnitsString = rawUnitsString.trim();

            Unit mUnit = Unit.fromName(rawUnitsString);
            if (mUnit == null)
            {
                List<Unit> commonUnits = KindOfQuantity.getSupportedUnits();
                throw new ConversionExceptionWithMessage("Unsupported Units value (" + rawUnitsString + ").  Supported values are: " + StringUtils.join(commonUnits, ", ") + ".");
            }
            if (defaultUnits != null && mUnit.kindOfQuantity != defaultUnits.kindOfQuantity)
                throw new ConversionExceptionWithMessage(String.format(CONVERSION_EXCEPTION_MESSAGE, rawUnits, defaultUnits));
            return mUnit;
        }
        return null;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testIsBase()
        {
            assertTrue(Unit.mL.isBase());
            assertFalse(Unit.L.isBase());
            assertTrue(Unit.g.isBase());
            assertFalse(Unit.kg.isBase());
            assertTrue(Unit.unit.isBase());
            assertFalse(Unit.count.isBase());
        }

        @Test
        public void testIsCompatible()
        {
            assertTrue(Unit.mL.isCompatible(Unit.uL));
            assertTrue(Unit.mL.isCompatible(Unit.L));
            assertFalse(Unit.mL.isCompatible(Unit.g));
            assertTrue(Unit.g.isCompatible(Unit.mg));
            assertFalse(Unit.g.isCompatible(Unit.mL));
            assertTrue(Unit.unit.isCompatible(Unit.count));
            assertFalse(Unit.unit.isCompatible(Unit.mL));
            assertFalse(Unit.mL.isCompatible(null));
        }

        @Test
        public void testBaseUnitValue()
        {
            assertEquals(1e0, Unit.mL.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e3, Unit.L.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e-3, Unit.uL.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e0, Unit.g.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e-3, Unit.mg.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e-6, Unit.ug.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e0, Unit.count.toBaseUnitValue(1.0), 0.00001);
        }

        @Test
        public void testFromBaseUnitValue()
        {
            assertEquals(1.0, Unit.mL.fromBaseUnitValue(1e0), 0.00001);
            assertEquals(1.0, Unit.L.fromBaseUnitValue(1e3), 0.00001);
            assertEquals(1.0, Unit.uL.fromBaseUnitValue(1e-3), 0.00001);
            assertEquals(1.0, Unit.g.fromBaseUnitValue(1e0), 0.00001);
            assertEquals(1.0, Unit.mg.fromBaseUnitValue(1e-3), 0.00001);
            assertEquals(1.0, Unit.ug.fromBaseUnitValue(1e-6), 0.00001);
            assertEquals(1.0, Unit.count.fromBaseUnitValue(1e0), 0.00001);
        }

        @Test
        public void testToStorageUnitValue()
        {
            assertEquals(1.0, Unit.mL.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000.0, Unit.L.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.001, Unit.uL.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.g.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.001, Unit.mg.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.000001, Unit.ug.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.count.toStorageUnitValue(1.0).doubleValue(), 0.00001);
        }

        @Test
        public void testFromStorageUnitValue()
        {
            assertEquals(1.0, Unit.mL.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.001, Unit.L.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000.0, Unit.uL.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.g.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000.0, Unit.mg.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000000.0, Unit.ug.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.count.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
        }

        @Test
        public void testFromName()
        {
            assertEquals(Unit.mL, Unit.fromName("ml"));
            assertEquals(Unit.mL, Unit.fromName("mL"));
            assertEquals(Unit.mL, Unit.fromName("milliliter"));
            assertEquals(Unit.mL, Unit.fromName("milliliters"));
            assertEquals(Unit.mL, Unit.fromName("millilitre"));
            assertEquals(Unit.mL, Unit.fromName("millilitres"));

            assertEquals(Unit.L, Unit.fromName("l"));
            assertEquals(Unit.L, Unit.fromName("L"));
            assertEquals(Unit.L, Unit.fromName("liter"));
            assertEquals(Unit.L, Unit.fromName("liters"));
            assertEquals(Unit.L, Unit.fromName("litre"));
            assertEquals(Unit.L, Unit.fromName("liters"));

            assertNull(Unit.fromName(null));
            assertNull(Unit.fromName(""));
        }

        @Test
        public void testGetValidatedUnit()
        {
            try
            {
                Unit.getValidatedUnit("g", Unit.mg);
                Unit.getValidatedUnit("g ", Unit.mg);
                Unit.getValidatedUnit(" g ", Unit.mg);
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("Compatible unit should not throw exception.");
            }
            try
            {
                assertNull(Unit.getValidatedUnit(null, Unit.unit));
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("null units should be null");
            }
            try
            {
                assertNull(Unit.getValidatedUnit("", Unit.unit));
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("empty units should be null");
            }
            try
            {
                Unit.getValidatedUnit("g", Unit.unit);
                fail("Units that are not comparable should throw exception.");
            }
            catch (ConversionExceptionWithMessage ignore)
            {

            }

            try
            {
                Unit.getValidatedUnit("nonesuch", Unit.unit);
                fail("Invalid units should throw exception.");
            }
            catch (ConversionExceptionWithMessage ignore)
            {

            }

        }

    }
}
