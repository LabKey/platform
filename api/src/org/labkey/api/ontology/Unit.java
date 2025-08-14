package org.labkey.api.ontology;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.function.Function;

public enum Unit
{
    no_unit(KindOfQuantity.Count, null, 1.0, "",
            Quantity.class,
            "no units", "no units"),

    count(KindOfQuantity.Count, null, 1.0, "",
            Quantity.class,
            "count", "count"),

    unit(KindOfQuantity.Count, null, 1.0, "",
            Quantity.class,
            "unit", "units"),

    // UCUM prefers "l", but "L" is also common and already supported by inventory (sorry Lambert)
    l(KindOfQuantity.Volume, null, 10e0, "l",
            Quantity.Volume_l.class,
            "liter", "liters",
            "L", "ℓ", "litre", "liters"),
    // is it better to include these little used units, to avoid future case-sensitivity problems?
    Ml(KindOfQuantity.Volume, l, 10e3, "Ml",
            Quantity.Volume_Megal.class,
            "megaliter", "megaliters",
            "ML", "megalitre", "megalitres"),
    kl(KindOfQuantity.Volume, l, 10e3, "kl",
            Quantity.Volume_kl.class,
            "kiloliter", "kiloliters",
            "kL", "kilolitre", "kilolitres"),
    ml(KindOfQuantity.Volume, l, 10e-3, "ml",
            Quantity.Volume_ml.class,
            "milliliter", "milliliters",
            "mL", "millilitre", "millilitres"),
    ul(KindOfQuantity.Volume, l, 10e-6, "ul",
            Quantity.Volume_ul.class,
            "microliter", "microliters",
            "uL", "μl", "μL", "microlitre", "microlitres"),
    nl(KindOfQuantity.Volume, l, 10e-9, "nl",
            Quantity.Volume_nl.class,
            "nanoliter", "nanoliters",
            "nL", "nanolitre", "nanolitres"),
    pl(KindOfQuantity.Volume, l, 10e-12, "pl",
            Quantity.Volume_pl.class,
            "picoliter", "picoliters",
            "pL", "picolitre", "picolitres"),

    g(KindOfQuantity.Mass, null, 10e0, "g",
            Quantity.Mass_g.class,
            "gram", "grams"),
    Mg(KindOfQuantity.Mass, g, 10e6, "Mg",
            Quantity.Mass_Megag.class,
            "megagram", "megagrams",
            "tonne", "tonnes"),
    kg(KindOfQuantity.Mass, g, 10e3, "kg",
            Quantity.Mass_kg.class,
            "kilogram", "kilograms"),
    mg(KindOfQuantity.Mass, g, 10e-3, "mg",
            Quantity.Mass_mg.class,
            "milligram", "milligrams"),
    ug(KindOfQuantity.Mass, g, 10e-6, "ug",
            Quantity.Mass_ug.class,
            "microgram", "micrograms",
        "μg"),
    ng(KindOfQuantity.Mass, g, 10e-9, "ng",
            Quantity.Mass_ng.class,
            "nanogram", "nanograms"),
    pg(KindOfQuantity.Mass, g, 10e-12, "pg",
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

    Unit(KindOfQuantity kind, Unit base, double value, @NotNull String printName,
         Class<? extends Quantity> quantityClass,
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
        return other.base == base;
    }

    public double toBaseUnitValue(double v)
    {
        return v * this.value;
    }

    public double fromBaseUnitValue(double v)
    {
        return v / this.value;
    }

    public Number toStorageUnitValue(Number v)
    {
        if (this == kindOfQuantity.getStorageUnit())
            return v;
        return convert(v.doubleValue(), this, kindOfQuantity.storageUnit);
    }

    public Number fromStorageUnitValue(Number v)
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


    public static Unit fromName(String unitName)
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

    public static double convert(double value, Unit from, Unit to)
    {
        if (from.base != to.base)
            throw new IllegalArgumentException("Can't convert " + from.name() + " to " + to.name());
        return from==to ? value : to.fromBaseUnitValue(from.toBaseUnitValue(value));
    }

    public Quantity convert(Object value)
    {
        return Quantity.convert(value, this);
    }
}
