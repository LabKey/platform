package org.labkey.api.ontology;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


/**
 * These helpers follow <a href="https://ucum.org/ucum">UCUM</a> conventions (or at least intend to).
 * <br>
 * NOTE: We have an initialization ordering issue as Unit and KindOfQuantity are both enums and they point to each other.
 * Just in case you were wondering why I don't use Unit directly in the constructor.
 */

public enum KindOfQuantity
{
    Volume("volume", "ml")
    {
        @Override
        public List<Unit> getCommonUnits()
        {
            return List.of(Unit.kL, Unit.L, Unit.mL, Unit.uL);
        }
    },

    Mass("mass", "g")
    {
        @Override
        public List<Unit> getCommonUnits()
        {
            return List.of(Unit.kg, Unit.g, Unit.mg, Unit.ug, Unit.ng);
        }
    },

    // Not a real unit per UCUM, but useful for annotation of "storage amount" for instance.
    Count("count", "unit")
    {
        @Override
        public List<Unit> getCommonUnits()
        {
            return List.of(Unit.blocks, Unit.bottles, Unit.boxes, Unit.cells, Unit.kits, Unit.packs, Unit.pieces, Unit.slides, Unit.tests, Unit.unit);
        }
    };

    final @NotNull String name;
    final @NotNull String storageUnitName;
    Unit storageUnit = null;

    KindOfQuantity(@NotNull String name, @NotNull String storageUnitName) // , List<Unit> list)
    {
        this.name = name;
        this.storageUnitName = storageUnitName;
    }

    @NotNull String getName()
    {
        return name;
    }

    /* default unit for data entry */
    @NotNull Unit getDefaultDisplayUnit()
    {
        return storageUnit;
    }

    /* unit used for database storage and in-memory representation of Quantity*/
    public Unit getStorageUnit()
    {
        if (null == storageUnit)
            storageUnit = Unit.fromName(storageUnitName);
        return storageUnit;
    }

    public Quantity toQuantity(Number n)
    {
        return Quantity.of(n, getStorageUnit());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean accept(Unit unit)
    {
        return getStorageUnit().base == unit.base;
    }

    public abstract List<Unit> getCommonUnits();

    static KindOfQuantity getKindOfQuantity(String name)
    {
        if ("volume".equalsIgnoreCase(name))
            return Volume;
        if ("vol".equalsIgnoreCase(name))
            return Volume;
        if ("mass".equalsIgnoreCase(name))
            return Mass;
        if ("weight".equalsIgnoreCase(name))
            return Mass;
        if ("count".equalsIgnoreCase(name))
            return Count;
        return null;
    }

    public static List<Unit> getSupportedUnits()
    {
        List<Unit> supported = new ArrayList<>();
        supported.addAll(Volume.getCommonUnits());
        supported.addAll(Mass.getCommonUnits());
        supported.addAll(Count.getCommonUnits());
        return supported;
    }

    // other potentially useful KindOfQuantity
    //
    // Fixed Unit e.g. arbitrary UCUM unit w/no conversion supported (basically a column annotation)
    // if we support Fixed Unit, then maybe KindOfQuantity is used sparingly
    //
    // * time (aka duration, not datetime): 60s
    // * length:        1m
    // * area           1m2
    // * temperature/change in temperature has pitfalls
    // * mass concentration
    // * per volume (e.g. count per volume) /ml
}

