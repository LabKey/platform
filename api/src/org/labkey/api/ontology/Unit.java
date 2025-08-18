package org.labkey.api.ontology;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConversionExceptionWithMessage;

import java.util.HashMap;
import java.util.function.Function;

public enum Unit
{
    unit(KindOfQuantity.Count, null, 1.0, "",
            Quantity.class,
            "unit", "units"),
    count(KindOfQuantity.Count, unit, 1.0, "",
            Quantity.class,
            "count", "count"),

    ml(KindOfQuantity.Volume, null, 1e0, "ml",
            Quantity.Volume_ml.class,
            "milliliter", "milliliters",
            "mL", "millilitre", "millilitres"),
    // UCUM prefers "l", but "L" is also common and already supported by inventory (sorry Lambert)
    l(KindOfQuantity.Volume, ml, 1e3, "l",
            Quantity.Volume_l.class,
            "liter", "liters",
            "L", "ℓ", "litre", "liters"),
    // is it better to include these little used units, to avoid future case-sensitivity problems?
    Ml(KindOfQuantity.Volume, ml, 1e9, "Ml",
            Quantity.Volume_Megal.class,
            "megaliter", "megaliters",
            "ML", "megalitre", "megalitres"),
    kl(KindOfQuantity.Volume, ml, 1e6, "kl",
            Quantity.Volume_kl.class,
            "kiloliter", "kiloliters",
            "kL", "kilolitre", "kilolitres"),
    ul(KindOfQuantity.Volume, ml, 1e-3, "ul",
            Quantity.Volume_ul.class,
            "microliter", "microliters",
            "uL", "μl", "μL", "microlitre", "microlitres"),
    nl(KindOfQuantity.Volume, ml, 1e-6, "nl",
            Quantity.Volume_nl.class,
            "nanoliter", "nanoliters",
            "nL", "nanolitre", "nanolitres"),
    pl(KindOfQuantity.Volume, ml, 1e-9, "pl",
            Quantity.Volume_pl.class,
            "picoliter", "picoliters",
            "pL", "picolitre", "picolitres"),

    g(KindOfQuantity.Mass, null, 1e0, "g",
            Quantity.Mass_g.class,
            "gram", "grams"),
    Mg(KindOfQuantity.Mass, g, 1e6, "Mg",
            Quantity.Mass_Megag.class,
            "megagram", "megagrams",
            "tonne", "tonnes"),
    kg(KindOfQuantity.Mass, g, 1e3, "kg",
            Quantity.Mass_kg.class,
            "kilogram", "kilograms"),
    mg(KindOfQuantity.Mass, g, 1e-3, "mg",
            Quantity.Mass_mg.class,
            "milligram", "milligrams"),
    ug(KindOfQuantity.Mass, g, 1e-6, "ug",
            Quantity.Mass_ug.class,
            "microgram", "micrograms",
        "μg"),
    ng(KindOfQuantity.Mass, g, 1e-9, "ng",
            Quantity.Mass_ng.class,
            "nanogram", "nanograms"),
    pg(KindOfQuantity.Mass, g, 1e-12, "pg",
            Quantity.Mass_pg.class,
            "picogram", "picograms");


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
    final double value;

    Unit(@NotNull KindOfQuantity kind, Unit base, double value, @NotNull String printName,
         @NotNull Class<? extends Quantity> quantityClass,
         @NotNull String singular, @NotNull String plural, String... otherNames)
    {
        this.kindOfQuantity = kind;
        this.base = null == base ? this : base;
        this.value = value;
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
        if (this == kindOfQuantity.getStorageUnit())
            return v;
        return convert(v.doubleValue(), this, kindOfQuantity.storageUnit);
    }

    public Number fromStorageUnitValue(@NotNull Number v)
    {
        if (this == kindOfQuantity.getStorageUnit())
            return v;
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
        if (from.base != to.base)
            throw new IllegalArgumentException("Can't convert " + from.name() + " to " + to.name());
        return from==to ? value : to.fromBaseUnitValue(from.toBaseUnitValue(value));
    }

    public Quantity convert(@Nullable Object value)
    {
        return Quantity.convert(value, this);
    }

    public static Unit getValidatedUnit(@Nullable String rawUnits, @Nullable Unit defaultUnits)
    {
        if (!StringUtils.isBlank(rawUnits))
        {
            rawUnits = rawUnits.trim();

            Unit mUnit = Unit.fromName(rawUnits);
            if (mUnit == null)
            {
                throw new ConversionExceptionWithMessage("Unsupported Units value (" + rawUnits + ").  Supported values are: " + StringUtils.join(Unit.values(), ", ") + ".");
            }
            if (defaultUnits != null && mUnit.kindOfQuantity != defaultUnits.kindOfQuantity)
                throw new ConversionExceptionWithMessage("Units value (" + rawUnits + ") cannot be converted to the default units (" + defaultUnits + ").");
            return mUnit;
        }
        return null;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testIsBase()
        {
            assertTrue(Unit.ml.isBase());
            assertFalse(Unit.l.isBase());
            assertTrue(Unit.g.isBase());
            assertFalse(Unit.kg.isBase());
            assertTrue(Unit.unit.isBase());
            assertFalse(Unit.count.isBase());
        }

        @Test
        public void testIsCompatible()
        {
            assertTrue(Unit.ml.isCompatible(Unit.ul));
            assertTrue(Unit.ml.isCompatible(Unit.l));
            assertFalse(Unit.ml.isCompatible(Unit.g));
            assertTrue(Unit.g.isCompatible(Unit.mg));
            assertFalse(Unit.g.isCompatible(Unit.ml));
            assertTrue(Unit.unit.isCompatible(Unit.count));
            assertFalse(Unit.unit.isCompatible(Unit.ml));
            assertFalse(Unit.ml.isCompatible(null));
        }

        @Test
        public void testBaseUnitValue()
        {
            assertEquals(1e0, Unit.ml.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e3, Unit.l.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e-3, Unit.ul.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e0, Unit.g.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e-3, Unit.mg.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e-6, Unit.ug.toBaseUnitValue(1.0), 0.00001);
            assertEquals(1e0, Unit.count.toBaseUnitValue(1.0), 0.00001);
        }

        @Test
        public void testFromBaseUnitValue()
        {
            assertEquals(1.0, Unit.ml.fromBaseUnitValue(1e0), 0.00001);
            assertEquals(1.0, Unit.l.fromBaseUnitValue(1e3), 0.00001);
            assertEquals(1.0, Unit.ul.fromBaseUnitValue(1e-3), 0.00001);
            assertEquals(1.0, Unit.g.fromBaseUnitValue(1e0), 0.00001);
            assertEquals(1.0, Unit.mg.fromBaseUnitValue(1e-3), 0.00001);
            assertEquals(1.0, Unit.ug.fromBaseUnitValue(1e-6), 0.00001);
            assertEquals(1.0, Unit.count.fromBaseUnitValue(1e0), 0.00001);
        }

        @Test
        public void testToStorageUnitValue()
        {
            assertEquals(1.0, Unit.ml.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000.0, Unit.l.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.001, Unit.ul.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.g.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.001, Unit.mg.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.000001, Unit.ug.toStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.count.toStorageUnitValue(1.0).doubleValue(), 0.00001);
        }

        @Test
        public void testFromStorageUnitValue()
        {
            assertEquals(1.0, Unit.ml.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(0.001, Unit.l.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000.0, Unit.ul.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.g.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000.0, Unit.mg.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1000000.0, Unit.ug.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
            assertEquals(1.0, Unit.count.fromStorageUnitValue(1.0).doubleValue(), 0.00001);
        }

        @Test
        public void testFromName()
        {
            assertEquals(Unit.ml, Unit.fromName("ml"));
            assertEquals(Unit.ml, Unit.fromName("mL"));
            assertEquals(Unit.ml, Unit.fromName("milliliter"));
            assertEquals(Unit.ml, Unit.fromName("milliliters"));
            assertEquals(Unit.ml, Unit.fromName("millilitre"));
            assertEquals(Unit.ml, Unit.fromName("millilitres"));

            assertEquals(Unit.l, Unit.fromName("l"));
            assertEquals(Unit.l, Unit.fromName("L"));
            assertEquals(Unit.l, Unit.fromName("liter"));
            assertEquals(Unit.l, Unit.fromName("liters"));
            assertEquals(Unit.l, Unit.fromName("litre"));
            assertEquals(Unit.l, Unit.fromName("liters"));

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
                Unit.getValidatedUnit(null, Unit.unit);
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("null units should validate");
            }
            try
            {
                Unit.getValidatedUnit("", Unit.unit);
            }
            catch (ConversionExceptionWithMessage e)
            {
                fail("empty units should validate");
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
