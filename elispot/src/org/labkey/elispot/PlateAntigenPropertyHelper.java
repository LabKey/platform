package org.labkey.elispot;

import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.exp.SamplePropertyHelper;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 14, 2008
 */
public class PlateAntigenPropertyHelper extends SamplePropertyHelper<WellGroupTemplate>
{
    private List<String> _antigenNames;
    private final PlateTemplate _template;

    public PlateAntigenPropertyHelper(PropertyDescriptor[] pds, PlateTemplate template)
    {
        super(pds);
        _template = template;
        _antigenNames = new ArrayList<String>();

        if (template != null)
        {
            for (WellGroupTemplate wellgroup : template.getWellGroups())
            {
                if (wellgroup.getType() == WellGroup.Type.ANTIGEN)
                {
                    _antigenNames.add(wellgroup.getName());
                }
            }
        }
    }

    protected WellGroupTemplate getObject(int index, Map<PropertyDescriptor, String> sampleProperties)
    {
        int i = 0;
        for (WellGroupTemplate wellgroup : _template.getWellGroups())
        {
            if (wellgroup.getType() == WellGroup.Type.ANTIGEN)
            {
                if (i == index)
                {
                    return wellgroup;
                }
                i++;
            }
        }
        throw new IndexOutOfBoundsException("Requested #" + index + " but there were only " + i + " well group templates");
    }

    protected boolean isCopyable(PropertyDescriptor pd)
    {
        return !AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(pd.getName()) && !AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(pd.getName());
    }


    public List<String> getSampleNames()
    {
        return _antigenNames;
    }

}
