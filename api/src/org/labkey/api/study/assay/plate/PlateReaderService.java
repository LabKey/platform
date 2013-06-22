/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.study.assay.plate;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.PlateBasedAssayProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: klum
 * Date: 10/9/12
 */
public class PlateReaderService
{
    public static final String PLATE_READER_PROPERTY = "PlateReader";
    public static final String READER_TYPE_PROPERTY = "FileType";

    private static Map<String, Map<String, PlateReader>> _readers = new HashMap<>();

    public static Map<String, PlateReader> getPlateReaders(PlateBasedAssayProvider provider)
    {
        return Collections.unmodifiableMap(_readers.get(provider.getName()));
    }

    public static PlateReader getPlateReader(PlateBasedAssayProvider provider, String type)
    {
        if (_readers.containsKey(provider.getName()))
        {
            return _readers.get(provider.getName()).get(type);
        }
        return null;
    }

    public static synchronized void registerPlateReader(PlateBasedAssayProvider provider, PlateReader reader)
    {
        if (!_readers.containsKey(provider.getName()))
            _readers.put(provider.getName(), new HashMap<String, PlateReader>());

        _readers.get(provider.getName()).put(reader.getType(), reader);
    }

    public static ListDefinition getPlateReaderList(PlateBasedAssayProvider provider, Container c)
    {
        Container lookupContainer = c.getProject();
        Map<String, ListDefinition> lists = ListService.get().getLists(lookupContainer);
        return lists.get(provider.getPlateReaderListName());
    }

    public static ListDefinition createPlateReaderList(Container c, User user, PlateBasedAssayProvider provider)
    {
        ListDefinition readerList = getPlateReaderList(provider, c);
        if (readerList == null)
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                Container lookupContainer = c.getProject();
                readerList = ListService.get().createList(lookupContainer, provider.getPlateReaderListName(), ListDefinition.KeyType.Varchar);

                DomainProperty nameProperty = addProperty(readerList.getDomain(), PLATE_READER_PROPERTY, PropertyType.STRING, null);
                nameProperty.setPropertyURI(readerList.getDomain().getTypeURI() + "#" + PLATE_READER_PROPERTY);
                DomainProperty typeProperty = addProperty(readerList.getDomain(), READER_TYPE_PROPERTY, PropertyType.STRING, null);
                typeProperty.setPropertyURI(readerList.getDomain().getTypeURI() + "#" + READER_TYPE_PROPERTY);

                readerList.setKeyName(nameProperty.getName());
                readerList.setTitleColumn(PLATE_READER_PROPERTY);

                readerList.save(user);

                transaction.commit();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return readerList;
    }

    public static PlateReader getPlateReaderFromName(String readerName, User u, Container c, PlateBasedAssayProvider provider)
    {
        ListDefinition list = PlateReaderService.getPlateReaderList(provider, c);
        if (list != null)
        {
            DomainProperty prop = list.getDomain().getPropertyByName(PlateReaderService.READER_TYPE_PROPERTY);
            ListItem item = list.getListItem(readerName, u);
            if (item != null && prop != null)
            {
                Object value = item.getProperty(prop);
                if (value instanceof OntologyManager.PropertyRow)
                    return PlateReaderService.getPlateReader(provider, ((OntologyManager.PropertyRow)value).getStringValue());
                else
                    return PlateReaderService.getPlateReader(provider, String.valueOf(value));
            }
        }
        return null;
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
}
