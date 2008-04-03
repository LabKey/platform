package org.labkey.elispot.plate;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 23, 2008
 */
public class ElispotPlateReaderService
{
    public static final String PLATE_READER_PROPERTY = "PlateReader";
    public static final String READER_TYPE_PROPERTY = "FileType";

    private static Map<String, I> _readers = new HashMap<String, I>();

    public static Map<String, I> getPlateReaders()
    {
        return Collections.unmodifiableMap(_readers);
    }

    public static I getPlateReader(String type)
    {
        return _readers.get(type);
    }

    public static synchronized void registerProvider(I provider)
    {
        _readers.put(provider.getType(), provider);
    }

    public static ListDefinition getPlateReaderList(Container c)
    {
        Container lookupContainer = c.getProject();
        Map<String, ListDefinition> lists = ListService.get().getLists(lookupContainer);
        return lists.get("ElispotPlateReader");
    }

    public static ListDefinition createPlateReaderList(Container c, User user)
    {
        ListDefinition readerList = getPlateReaderList(c);
        if (readerList == null)
        {
            Container lookupContainer = c.getProject();
            readerList = ListService.get().createList(lookupContainer, "ElispotPlateReader");

            DomainProperty nameProperty = addProperty(readerList.getDomain(), PLATE_READER_PROPERTY, PropertyType.STRING, null);
            nameProperty.setPropertyURI(readerList.getDomain().getTypeURI() + "#" + PLATE_READER_PROPERTY);
            DomainProperty typeProperty = addProperty(readerList.getDomain(), READER_TYPE_PROPERTY, PropertyType.STRING, null);
            typeProperty.setPropertyURI(readerList.getDomain().getTypeURI() + "#" + READER_TYPE_PROPERTY);

            try {
                readerList.setKeyName(nameProperty.getName());
                readerList.setKeyType(ListDefinition.KeyType.Varchar);
                readerList.setDescription("Elispot Plate Reader Types");
                readerList.setTitleColumn(PLATE_READER_PROPERTY);

                readerList.save(user);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return readerList;
    }

    protected static DomainProperty addProperty(Domain domain, String name, PropertyType type, String description)
    {
        DomainProperty prop = domain.addProperty();
        prop.setLabel(name);
        prop.setName(name);
        prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
        prop.setDescription(description);
        return prop;
    }

    public interface I
    {
        public String getType();

        public double[][] loadFile(PlateTemplate template, File dataFile) throws ExperimentException;
    }
}
