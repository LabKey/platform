package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.AbstractPlateLayoutHandler;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TsvPlateLayoutHandler extends AbstractPlateLayoutHandler
{
    public static final String BLANK_PLATE = "blank";
    public static final String TYPE = "Standard";

    @Override
    public String getAssayType()
    {
        return TYPE;
    }

    @Override
    @NotNull
    public List<String> getLayoutTypes(PlateType plateType)
    {
        return Collections.singletonList(BLANK_PLATE);
    }

    @Override
    public Plate createTemplate(@Nullable String templateTypeName, Container container, @NotNull PlateType plateType)
    {
        validatePlateType(plateType);
        Plate template = PlateService.get().createPlateTemplate(container, getAssayType(), plateType);

        template.addWellGroup("Positive", WellGroup.Type.CONTROL, Collections.emptyList());
        template.addWellGroup("Negative", WellGroup.Type.CONTROL, Collections.emptyList());

        return template;
    }

    @Override
    protected List<Pair<Integer, Integer>> getSupportedPlateSizes()
    {
        return List.of(new Pair<>(3, 4),
                new Pair<>(4, 6),
                new Pair<>(6, 8),
                new Pair<>(8, 12),
                new Pair<>(16, 24),
                new Pair<>(32, 48));
    }

    @Override
    public List<WellGroup.Type> getWellGroupTypes()
    {
        return Arrays.asList(WellGroup.Type.CONTROL, WellGroup.Type.SAMPLE);
    }
}