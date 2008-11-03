/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.xarassay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.view.HttpView;

import java.util.Map;

/**
 * User: phussey
 * Date: Sep 17, 2007
 * Time: 12:30:55 AM
 */
public class CptacAssayProvider extends XarAssayProvider
{
    // todo:  check if these should have differnt names from MsFractionAssayProvider class
    public static final String PROTOCOL_LSID_NAMESPACE_PREFIX = "CptacAssayProtocol";
    public static final String NAME = "Cptac";
    public static final String DATA_LSID_PREFIX = "MZXMLData";
    public static final DataType MS_ASSAY_DATA_TYPE = new DataType(DATA_LSID_PREFIX);

    public CptacAssayProvider()
    {
        super(PROTOCOL_LSID_NAMESPACE_PREFIX, RUN_LSID_NAMESPACE_PREFIX, MS_ASSAY_DATA_TYPE);
    }


    public String getProtocolLsidNamespacePrefix()
    {
        return PROTOCOL_LSID_NAMESPACE_PREFIX;
    }

  /*  public String getRunLsidNamespacePrefix()
    {
        return RUN_LSID_NAMESPACE_PREFIX;
    }

    public String getRunLsidObjectIdPrefix()
    {
        return RUN_LSID_OBJECT_ID_PREFIX;
    }

*/    public String getName()
    {
        return NAME;
    }


    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return null;

    }

    protected Domain createUploadSetDomain(Container c, User user)
    {

        Domain uploadSetDomain = super.createUploadSetDomain(c, user);
        Container listContainer = c.getProject();
        Map<String, ListDefinition> lists = ListService.get().getLists(listContainer);

        /*
        Site lookup, example of pre-population
         */
        String baseName = "PerformanceSite";
        String listName = baseName + "List";

        ListDefinition cptacList = lists.get(listName);
        if (cptacList == null)
        {
            cptacList = ListService.get().createList(listContainer, listName);
            DomainProperty nameProperty = addProperty(cptacList.getDomain(), "Name", PropertyType.STRING);
            addProperty(cptacList.getDomain(), "ContactPerson",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "ContactEmail",PropertyType.STRING);
            cptacList.setKeyName("PSiteID");
            cptacList.setTitleColumn(nameProperty.getName());
            cptacList.setKeyType(ListDefinition.KeyType.Varchar);
            cptacList.setDescription("Labs performing Cptac assays");
            try
            {
                cptacList.save(user);

                ListItem listItem = cptacList.createListItem();
                listItem.setKey("IUIPI");
                listItem.setProperty(nameProperty, "IUIPI");
                listItem.save(user);

                listItem = cptacList.createListItem();
                listItem.setKey("INCAPS");
                listItem.setProperty(nameProperty, "INCAPS");
                listItem.save(user);

                listItem = cptacList.createListItem();
                listItem.setKey("PURDUE");
                listItem.setProperty(nameProperty, "Purdue");
                listItem.save(user);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        DomainProperty cptacProperty = addProperty(uploadSetDomain, baseName, PropertyType.STRING);
        cptacProperty.setRequired(true);
        cptacProperty.setLookup(new Lookup(listContainer, "lists", cptacList.getName()));


        /*
        Team List
         */
        baseName="StudyTeam";
        listName = baseName + "List";

        cptacList = lists.get(listName);
        if (cptacList == null)
        {
            cptacList = ListService.get().createList(listContainer, listName);
            DomainProperty nameProperty = addProperty(cptacList.getDomain(), "Name", PropertyType.STRING);
            addProperty(cptacList.getDomain(), "PIName",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "PIEmail",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "ContactPerson",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "ContactEmail",PropertyType.STRING);
            cptacList.setKeyName("TeamID");
            cptacList.setTitleColumn(nameProperty.getName());
            cptacList.setKeyType(ListDefinition.KeyType.Varchar);
            cptacList.setDescription("Study Teams");
            try
            {
                cptacList.save(user);

            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        cptacProperty = addProperty(uploadSetDomain, baseName, PropertyType.STRING);
        cptacProperty.setLookup(new Lookup(listContainer, "lists", cptacList.getName()));


        /*
            MS Instrument.  Type property could be made a lookup itself, and should match type tag written to mzxml
         */
        baseName = "MSInstrument";
        listName = baseName + "List";

        cptacList = lists.get(listName);
        if (cptacList == null)
        {
            cptacList = ListService.get().createList(listContainer, listName);
            DomainProperty nameProperty = addProperty(cptacList.getDomain(), "Name", PropertyType.STRING);
            addProperty(cptacList.getDomain(), "Type",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "Manufacture",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "ContactPerson",PropertyType.STRING);
            cptacList.setKeyName("MSInstrumentID");
            cptacList.setTitleColumn(nameProperty.getName());
            cptacList.setKeyType(ListDefinition.KeyType.Varchar);
            cptacList.setDescription("List of physical MS Instruments");
            try
            {
                cptacList.save(user);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        cptacProperty = addProperty(uploadSetDomain, baseName, PropertyType.STRING);
        cptacProperty.setLookup(new Lookup(listContainer, "lists", cptacList.getName()));


        /*
            Reagent(s)
         */
        baseName = "Reagent";
        listName = baseName + "List";
        cptacList = lists.get(listName);
        if (cptacList == null)
        {
            cptacList = ListService.get().createList(listContainer, listName);
            DomainProperty nameProperty = addProperty(cptacList.getDomain(), "Name", PropertyType.STRING);
            addProperty(cptacList.getDomain(), "Type",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "Manufacture",PropertyType.STRING);
            addProperty(cptacList.getDomain(), "Concentration",PropertyType.DOUBLE);
            addProperty(cptacList.getDomain(), "Time",PropertyType.DOUBLE);
            addProperty(cptacList.getDomain(), "Temperature",PropertyType.DOUBLE);
            cptacList.setKeyName("ReagentID");
            cptacList.setTitleColumn(nameProperty.getName());
            cptacList.setKeyType(ListDefinition.KeyType.Varchar);
            cptacList.setDescription("List of reagents used in Sample Preparation Step");
            try
            {
                cptacList.save(user);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        /*
            Example of set of same properties
         */
        int numReagents = 2;
        for (int i=0; i<numReagents; i++)
        {
            String propertyName = baseName;
            if (i>0)
                propertyName = baseName + "_" + i;

            cptacProperty = addProperty(uploadSetDomain, propertyName, PropertyType.STRING);
            cptacProperty.setLookup(new Lookup(listContainer, "lists", cptacList.getName()));
        }

        /*
            LCColumn
         */

        baseName = "LCColumn";
        listName = baseName + "List";

        cptacList = lists.get(listName);
        if (cptacList == null)
        {
            cptacList = ListService.get().createList(listContainer, listName);
            DomainProperty nameProperty = addProperty(cptacList.getDomain(), "Name", PropertyType.STRING);
            addProperty(cptacList.getDomain(), "Manufacture", PropertyType.STRING);
            addProperty(cptacList.getDomain(), "InnerDiameter", PropertyType.DOUBLE);
            addProperty(cptacList.getDomain(), "Length", PropertyType.DOUBLE);
            addProperty(cptacList.getDomain(), "Temp", PropertyType.DOUBLE);
            addProperty(cptacList.getDomain(), "FlowRate", PropertyType.DOUBLE);
            addProperty(cptacList.getDomain(), "InjectionVolume", PropertyType.DOUBLE);
            cptacList.setKeyName("LCColTypeID");
            cptacList.setTitleColumn(nameProperty.getName());
            cptacList.setKeyType(ListDefinition.KeyType.Varchar);
            cptacList.setDescription("Type of LC Column Used in Separation Step");
            try
            {
                cptacList.save(user);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        cptacProperty = addProperty(uploadSetDomain, baseName, PropertyType.STRING);
        cptacProperty.setLookup(new Lookup(listContainer, "lists", cptacList.getName()));

        return uploadSetDomain;
    }


    protected DomainProperty addProperty(Domain domain, String name, PropertyType type)
    {
        DomainProperty dp = super.addProperty(domain, name, type);
        if (dp.getPropertyURI()==null)
            dp.setPropertyURI(domain.getTypeURI() + "#" + name);
        return dp;
    }

    protected Domain createRunDomain(Container c, User user)
    {
        Domain runDomain = super.createRunDomain(c, user);
        addProperty(runDomain, "SOP","SOP", PropertyType.BOOLEAN, "Standard Operating Procedure used for this run");
        addProperty(runDomain, "StartDate", "Start Date", PropertyType.DATE_TIME, "Date that this run was performed (not upload date). ");

        return runDomain;
    }


}
