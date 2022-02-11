/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.specimen.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.BaseAbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.importer.EventVialRollup;
import org.labkey.api.specimen.importer.RollupHelper;
import org.labkey.api.specimen.importer.RollupInstance;
import org.labkey.api.specimen.importer.VialSpecimenRollup;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.data.xml.TableType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractSpecimenDomainKind extends BaseAbstractDomainKind
{
    protected static final String COMMENTS = "Comments";                   // Reserved field name for Vial and Specimen
    protected static final String COLUMN = "Column";                       // Reserved field name for Vial, Specimen and Event

    abstract protected String getNamespacePrefix();
    abstract public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(@Nullable SpecimenTablesTemplate template);
    public AbstractSpecimenDomainKind()
    {
        super();
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        Lsid lsid = new Lsid(getNamespacePrefix(), container.getId(), schemaName.toLowerCase() + "-" + queryName.toLowerCase());
        return lsid.toString();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(containerUser.getContainer());  // TODO: view specimen grid
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix() != null && getNamespacePrefix().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        return new HashSet<>();
    }

    @Override
    public DbScope getScope()
    {
        return SpecimenSchema.get().getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return SpecimenTablesProvider.SCHEMA_NAME;
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public String getMetaDataSchemaName()
    {
        return "study";
    }

    @Override
    public String getMetaDataTableName()
    {
        return getKindName();
    }

    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container, SpecimenTablesProvider provider)
    {
        return Collections.emptySet();
    }

    protected void setForeignKeyTableInfos(Container container, Set<PropertyStorageSpec.ForeignKey> foreignKeys, SpecimenTablesProvider provider)
    {
        // If this table requires FK to other provisioned tables (must be in same dbschema), get those tables
        for (PropertyStorageSpec.ForeignKey foreignKey : foreignKeys)
        {
            if (foreignKey.isProvisioned())
            {
                Domain domain = provider.getDomain(foreignKey.getTableName(), true);
                if (null == domain)
                    throw new IllegalStateException("Expected domain to be created if it didn't already exist.");

                TableInfo tableInfo = StorageProvisioner.createTableInfo(domain);
                foreignKey.setTableInfoProvisioned(tableInfo);
            }
        }
    }

    @Override
    public void afterLoadTable(SchemaTableInfo ti, Domain domain)
    {
        // Grab the meta data for this table (event, vial, or specimen) and apply it to the provisioned table
        DbSchema studySchema = SpecimenSchema.get().getSchema();
        TableType xmlTable = studySchema.getTableXmlMap().get(getMetaDataTableName());
        ti.loadTablePropertiesFromXml(xmlTable, true);
    }

    protected ValidationException checkRollups(
            @Nullable List<PropertyDescriptor> vialProps,             // all of these are nonBase properties
            @Nullable List<PropertyDescriptor> specimenProps,          // all of these are nonBase properties
            Container container,
            User user,
            ValidationException exception,
            boolean addWarnings)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);
        Domain eventDomain = specimenTablesProvider.getDomain("SpecimenEvent", false);
        Domain vialDomain = specimenTablesProvider.getDomain("vial", false);
        Domain specimenDomain = specimenTablesProvider.getDomain("specimen", false);

        boolean editingSpecimen = null != specimenProps;
        boolean editingVial = null != vialProps;

        List<PropertyDescriptor> eventProps = null;
        if (null != eventDomain)
        {
            eventProps = getPropertyDescriptorsForDomain(eventDomain, container);
        }

        if (null == vialProps && null != vialDomain)
        {
            vialProps = getPropertyDescriptorsForDomain(vialDomain, container);
        }

        if (null == specimenProps && null != specimenDomain)
        {
            specimenProps = getPropertyDescriptorsForDomain(specimenDomain, container);
        }

        if (null != eventDomain)
        {   // Consider that rollups can come from base properties
            for (DomainProperty domainProperty : eventDomain.getBaseProperties())
                eventProps.add(domainProperty.getPropertyDescriptor());
        }
        CaseInsensitiveHashSet eventFieldNamesDisallowedForRollups = RollupHelper.getEventFieldNamesDisallowedForRollups();
        Map<String, Pair<String, RollupInstance<EventVialRollup>>> vialToEventNameMap = RollupHelper.getVialToEventNameMap(vialProps, eventProps);     // includes rollups with type mismatches

        if (editingVial)
        {
            for (PropertyDescriptor prop : vialProps)
            {
                Pair<String, RollupInstance<EventVialRollup>> eventPair = vialToEventNameMap.get(prop.getName().toLowerCase());
                if (null != eventPair)
                {
                    String eventFieldName = eventPair.first;
                    if (eventFieldNamesDisallowedForRollups.contains(eventFieldName))
                        exception.addError(new SimpleValidationError("You may not rollup from SpecimenEvent field '" + eventFieldName + "'."));
                    else if (!eventPair.second.isTypeConstraintMet())
                        exception.addError(new SimpleValidationError("SpecimenEvent field '" + eventFieldName + "' would rollup to '" + prop.getName() + "' except the type constraint is not met."));
                }
                else if (addWarnings)
                    exception.addError(new SimpleValidationError("Your Vial field '" + prop.getName() + "', does not have a matching field in the SpecimenEvent table for a vial rollup calculation.", prop.getName(), ValidationException.SEVERITY.WARN));
            }
        }

        if (null != vialDomain)
        {   // Consider that rollups can come from base properties
            for (DomainProperty domainProperty : vialDomain.getBaseProperties())
                vialProps.add(domainProperty.getPropertyDescriptor());
        }
        CaseInsensitiveHashSet vialFieldNamesDisallowedForRollups = RollupHelper.getVialFieldNamesDisallowedForRollups();
        Map<String, Pair<String, RollupInstance<VialSpecimenRollup>>> specimenToVialNameMap = RollupHelper.getSpecimenToVialNameMap(specimenProps, vialProps);     // includes rollups with type mismatches

        if (editingSpecimen)
        {
            for (PropertyDescriptor prop : specimenProps)
            {
                Pair<String, RollupInstance<VialSpecimenRollup>> vialPair = specimenToVialNameMap.get(prop.getName().toLowerCase());
                if (null != vialPair)
                {
                    String vialFieldName = vialPair.first;
                    if (vialFieldNamesDisallowedForRollups.contains(vialFieldName))
                        exception.addError(new SimpleValidationError("You may not rollup from Vial field '" + vialFieldName + "'."));
                    else if (!vialPair.second.isTypeConstraintMet())
                        exception.addError(new SimpleValidationError("Vial field '" + vialFieldName + "' would rollup to '" + prop.getName() + "' except the type constraint is not met."));
                }
                else if (addWarnings)
                    exception.addError(new SimpleValidationError("Your Specimen field '" + prop.getName() + "', does not have a matching field in the Vial table for a specimen rollup calculation.", prop.getName(), ValidationException.SEVERITY.WARN));
            }
        }

        return exception;
    }

    private List<PropertyDescriptor> getPropertyDescriptorsForDomain(Domain domain, Container container)
    {
        Set<String> mandatoryProperties = getMandatoryPropertyNames(domain);
        List<PropertyDescriptor> pds = new ArrayList<>();
        for (DomainProperty prop : domain.getProperties())
        {
            if (null != prop.getName() && !mandatoryProperties.contains(prop.getName()))
            {
                pds.add(OntologyManager.getPropertyDescriptor(prop.getPropertyURI(), container));
            }
        }
        return pds;
    }

    protected PropertyDescriptor getPropFromGwtProp(GWTPropertyDescriptor gwtProp)
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setRangeURI(gwtProp.getRangeURI());
        pd.setConceptURI(gwtProp.getConceptURI());
        pd.setName(gwtProp.getName());
        return pd;
    }
}
