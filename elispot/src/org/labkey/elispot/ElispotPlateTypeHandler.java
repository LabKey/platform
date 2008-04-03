package org.labkey.elispot;

import org.labkey.api.data.Container;
import org.labkey.api.study.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 14, 2008
 */
public class ElispotPlateTypeHandler implements PlateTypeHandler
{
    public String getAssayType()
    {
        return "ELISpot";
    }

    public List<String> getTemplateTypes()
    {
        List<String> names = new ArrayList<String>();
        names.add("Default");
        return names;
    }

    public PlateTemplate createPlate(String templateTypeName, Container container) throws SQLException
    {
        PlateTemplate template = PlateService.get().createPlateTemplate(container, getAssayType());

        for (int sample = 0; sample < 4; sample++)
        {
            int row = sample * 2;
            // create the overall specimen group, consisting of two adjacent rows:
            template.addWellGroup("Specimen " + (sample + 1), WellGroup.Type.SPECIMEN,
                    PlateService.get().createPosition(container, row, 0),
                    PlateService.get().createPosition(container, row+1, template.getColumns() - 1));
        }

        if (templateTypeName != null && templateTypeName.equalsIgnoreCase("Default"))
        {
            // populate the antigen groups
            for (int antigen = 0; antigen < 4; antigen++)
            {
                List<Position> position1 = new ArrayList<Position>();
                List<Position> position2 = new ArrayList<Position>();

                for (int sample = 0; sample < 4; sample++)
                {
                    int row = sample * 2;
                    int col = antigen * 3;

                    position1.add(template.getPosition(row, col));
                    position1.add(template.getPosition(row, col + 1));
                    position1.add(template.getPosition(row, col + 2));

                    position2.add(template.getPosition(row + 1, col));
                    position2.add(template.getPosition(row + 1, col + 1));
                    position2.add(template.getPosition(row + 1, col + 2));
                }
                template.addWellGroup("Antigen " + (antigen*2 + 1), WellGroup.Type.ANTIGEN, position1);
                template.addWellGroup("Antigen " + (antigen*2 + 2), WellGroup.Type.ANTIGEN, position2);
            }
        }

        return template;
    }

    public WellGroup.Type[] getWellGroupTypes()
    {
        return new WellGroup.Type[]{
                WellGroup.Type.CONTROL, WellGroup.Type.SPECIMEN,
                WellGroup.Type.REPLICATE, WellGroup.Type.ANTIGEN};
    }
}

