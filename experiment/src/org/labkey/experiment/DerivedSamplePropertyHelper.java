package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.security.User;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.*;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public class DerivedSamplePropertyHelper extends SamplePropertyHelper<String>
{
    private List<String> _names;
    private final Map<Integer, String> _lsids = new HashMap<Integer, String>();
    private final ExpSampleSet _sampleSet;
    private final int _sampleCount;
    private final Container _container;
    private final User _user;


    public DerivedSamplePropertyHelper(ExpSampleSet sampleSet, int sampleCount, Container c, User user)
    {
        super(getPropertyDescriptors(sampleSet, c));
        _sampleSet = sampleSet;
        _sampleCount = sampleCount;
        _container = c;
        _user = user;
        _names = new ArrayList<String>();
        for (int i = 1; i <= sampleCount; i++)
        {
            _names.add("Output Sample " + i);
        }
    }

    public ExpSampleSet getSampleSet()
    {
        return _sampleSet;
    }

    public List<String> getSampleNames()
    {
        return _names;
    }

    protected String getObject(int index, Map<PropertyDescriptor, String> sampleProperties) throws DuplicateMaterialException
    {
        String lsid = _lsids.get(index);
        if (lsid == null)
        {
            String name = determineMaterialName(sampleProperties);
            if (_sampleSet == null)
            {
                XarContext context = new XarContext("DeriveSamples", _container, _user);
                try
                {
                    lsid = LsidUtils.resolveLsidFromTemplate("${FolderLSIDBase}:" + name, context, "Material");
                }
                catch (XarFormatException e)
                {
                    // Shouldn't happen - our template is safe
                    throw new RuntimeException(e);
                }
            }
            else
            {
                lsid = _sampleSet.getMaterialLSIDPrefix() + name;
            }

            if (_lsids.containsValue(lsid) || ExperimentService.get().getExpMaterial(lsid) != null)
            {
                throw new DuplicateMaterialException("Duplicate material name: " + name, getSpecimenPropertyInputName(getSampleNames().get(index), getNamePDs().get(0)));
            }
            _lsids.put(index, lsid);
        }
        return lsid;
    }

    public String determineMaterialName(Map<PropertyDescriptor, String> sampleProperties)
    {
        String separator = "";
        StringBuilder sb = new StringBuilder();
        for (PropertyDescriptor pd : getNamePDs())
        {
            sb.append(separator);
            separator = "-";
            sb.append(sampleProperties.get(pd));
        }
        return sb.toString();
    }

    protected boolean isCopyable(PropertyDescriptor pd)
    {
        return !getNamePDs().contains(pd);
    }

    public static PropertyDescriptor[] getPropertyDescriptors(ExpSampleSet sampleSet, Container c)
    {
        List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();

        if (sampleSet != null && sampleSet.getType() != null)
        {
            for (DomainProperty domainProp : sampleSet.getType().getProperties())
            {
                pds.add(domainProp.getPropertyDescriptor());
            }
        }
        else
        {
            PropertyDescriptor namePD = new PropertyDescriptor(ExperimentServiceImpl.get().getTinfoMaterial().getColumn("Name"), c);
            namePD.setRequired(true);
            pds.add(namePD);
        }
        
        return pds.toArray(new PropertyDescriptor[pds.size()]);
    }

    public List<PropertyDescriptor> getNamePDs()
    {
        if (_sampleSet != null)
        {
            return _sampleSet.getIdCols();
        }
        else
        {
            assert _propertyDescriptors[0].getName().equals("Name");
            return Collections.singletonList(_propertyDescriptors[0]);
        }
    }

}
