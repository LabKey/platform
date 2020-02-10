package org.labkey.assay.plate;

import org.labkey.api.assay.plate.AbstractPlateTypeHandler;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TsvPlateTypeHandler extends AbstractPlateTypeHandler
{
    public static final String BLANK_PLATE = "blank";

    @Override
    public String getAssayType()
    {
        return "GPAT (General)";
    }

    @Override
    public List<String> getTemplateTypes(Pair<Integer, Integer> size)
    {
        return Collections.singletonList(BLANK_PLATE);
    }

    @Override
    public PlateTemplate createPlate(String templateTypeName, Container container, int rowCount, int colCount) throws SQLException
    {
        PlateTemplate template = PlateService.get().createPlateTemplate(container, getAssayType(), rowCount, colCount);

        template.addWellGroup("Positive", WellGroup.Type.CONTROL, Collections.emptyList());
        template.addWellGroup("Negative", WellGroup.Type.CONTROL, Collections.emptyList());

        return template;
    }

    @Override
    public List<Pair<Integer, Integer>> getSupportedPlateSizes()
    {
        List<Pair<Integer, Integer>> sizes = new ArrayList<>();
        sizes.add(new Pair<>(8, 12));
        sizes.add(new Pair<>(16, 24));

        return sizes;
    }

    @Override
    public List<WellGroup.Type> getWellGroupTypes()
    {
        return Arrays.asList(WellGroup.Type.CONTROL, WellGroup.Type.SAMPLE);
    }
}
