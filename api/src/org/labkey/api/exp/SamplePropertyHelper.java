package org.labkey.api.exp;

import org.labkey.api.data.*;
import org.labkey.api.security.User;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public abstract class SamplePropertyHelper<ObjectType>
{
    protected final PropertyDescriptor[] _propertyDescriptors;
    private Map<PropertyDescriptor, DisplayColumnGroup> _groups;

    public SamplePropertyHelper(PropertyDescriptor[] propertyDescriptors)
    {
        _propertyDescriptors = propertyDescriptors;
    }

    public abstract List<String> getSampleNames();
    protected abstract ObjectType getObject(int index, Map<PropertyDescriptor, String> sampleProperties) throws DuplicateMaterialException;

    public static String getSpecimenPropertyInputName(String sampleName, PropertyDescriptor property)
    {
        return ColumnInfo.propNameFromName(sampleName + "_" + property.getName());
    }

    protected abstract boolean isCopyable(PropertyDescriptor pd);

    public Map<ObjectType, Map<PropertyDescriptor, String>> getSampleProperties(HttpServletRequest request) throws DuplicateMaterialException
    {
        Map<ObjectType, Map<PropertyDescriptor, String>> result = new LinkedHashMap<ObjectType, Map<PropertyDescriptor, String>>();
        List<String> names = getSampleNames();
        for (int i = 0; i < names.size(); i++)
        {
            Map<PropertyDescriptor, String> sampleProperties = new HashMap<PropertyDescriptor, String>();
            for (PropertyDescriptor property : _propertyDescriptors)
            {
                String inputName = getSpecimenPropertyInputName(names.get(i), property);
                sampleProperties.put(property, request.getParameter(inputName));
            }
            result.put(getObject(i, sampleProperties), sampleProperties);
        }
        return result;
    }

    public void addSampleColumns(DataRegion region, User user)
    {
        List<String> sampleNames = getSampleNames();
        if (sampleNames.isEmpty())
            return;
        _groups = new HashMap<PropertyDescriptor, DisplayColumnGroup>();
        for (PropertyDescriptor sampleProperty : _propertyDescriptors)
        {
            List<DisplayColumn> cols = new ArrayList<DisplayColumn>();
            for (String name : getSampleNames())
            {
                ColumnInfo col = sampleProperty.createColumnInfo(OntologyManager.getTinfoObject(), "ObjectURI", user);
                col.setName(getSpecimenPropertyInputName(name, sampleProperty));
                cols.add(new SpecimenInputColumn(col, name));
            }
            DisplayColumnGroup group = new DisplayColumnGroup(cols, sampleProperty.getName(), isCopyable(sampleProperty));
            _groups.put(sampleProperty, group);
            region.addGroup(group);
        }
        region.setGroupHeadings(sampleNames);
        region.setHorizontalGroups(true);
    }

    public Map<PropertyDescriptor, DisplayColumnGroup> getGroups()
    {
        return _groups;
    }

    public Map<PropertyDescriptor, String> getPostedPropertyValues(HttpServletRequest request)
    {
        Map<PropertyDescriptor, String> result = new HashMap<PropertyDescriptor, String>();
        for (String sampleName : getSampleNames())
        {
            for (PropertyDescriptor sampleProperty : _propertyDescriptors)
            {
                String name = getSpecimenPropertyInputName(sampleName, sampleProperty);
                PropertyDescriptor copy = sampleProperty.clone();
                copy.setPropertyId(0);
                copy.setName(name);
                String value = ColumnInfo.propNameFromName(name);
                result.put(copy, request.getParameter(value));
            }
        }
        return result;
    }

    private class SpecimenInputColumn extends DataColumn
    {
        public SpecimenInputColumn(ColumnInfo col, String sampleName)
        {
            super(col);
        }

        public boolean isEditable()
        {
            return true;
        }

        protected Object getInputValue(RenderContext ctx)
        {
            TableViewForm viewForm = ctx.getForm();
            return viewForm.getStrings().get(getName());
        }
    }
}
