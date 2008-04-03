package org.labkey.api.study.assay;

import org.labkey.api.exp.SamplePropertyHelper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.study.WellGroup;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public class PlateSamplePropertyHelper extends SamplePropertyHelper<WellGroupTemplate>
{
    private List<String> _sampleNames;
    private final PlateTemplate _template;

    public PlateSamplePropertyHelper(PropertyDescriptor[] pds, PlateTemplate template)
    {
        super(pds);
        _template = template;
        _sampleNames = new ArrayList<String>();

        if (template != null)
        {
            for (WellGroupTemplate wellgroup : template.getWellGroups())
            {
                if (wellgroup.getType() == WellGroup.Type.SPECIMEN)
                {
                    _sampleNames.add(wellgroup.getName());
                }
            }
        }
    }

    protected WellGroupTemplate getObject(int index, Map<PropertyDescriptor, String> sampleProperties)
    {
        int i = 0;
        for (WellGroupTemplate wellgroup : _template.getWellGroups())
        {
            if (wellgroup.getType() == WellGroup.Type.SPECIMEN)
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
        return _sampleNames;
    }
}
