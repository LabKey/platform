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
package org.labkey.study.model;

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
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.data.xml.TableType;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractSpecimenDomainKind extends AbstractDomainKind
{
    protected static final String COMMENTS = "Comments";                   // Reserved field name for Vial and Specimen
    protected static final String COLUMN = "Column";                       // Reserved field name for Vial, Specimen and Event

    abstract protected String getNamespacePrefix();
    abstract public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(@Nullable SpecimenTablesTemplate template);
    public AbstractSpecimenDomainKind()
    {
        super();
    }

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

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }

    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return new ActionURL(StudyController.ManageStudyAction.class, containerUser.getContainer());   // TODO: view specimen grid
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix() != null && getNamespacePrefix().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return new HashSet<>();
    }

    @Override
    public DbScope getScope()
    {
        return StudySchema.getInstance().getSchema().getScope();
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
        DbSchema studySchema = StudySchema.getInstance().getSchema();
        TableType xmlTable = studySchema.getTableXmlMap().get(getMetaDataTableName());
        ti.loadTablePropertiesFromXml(xmlTable, true);
    }

    protected ValidationException addErrorsToValidationException(List<String> errors, ValidationException exception)
    {
        exception.addError(new SimpleValidationError(getMessageString(errors)));
        return exception;
    }

    protected ValidationException addWarningsToValidationException(List<String> warnings, ValidationException exception)
    {
        exception.addWarning(getMessageString(warnings));
        return exception;
    }

    private String getMessageString(List<String> messages)
    {
        String sep = "";
        String msgString = "";
        for (String msg : messages)
        {
            msgString += sep + msg;
            sep = "\n";
        }
        return msgString;
    }

    protected SpecimenDomainRollupErrorsAndWarning checkRollups(
            @Nullable List<PropertyDescriptor> eventProps,            // all of these are nonBase properties
            @Nullable List<PropertyDescriptor> vialProps,             // all of these are nonBase properties
            @Nullable List<PropertyDescriptor> specimenProps,          // all of these are nonBase properties
            Container container,
            User user)
    {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);
        Domain eventDomain = specimenTablesProvider.getDomain("SpecimenEvent", false);
        Domain vialDomain = specimenTablesProvider.getDomain("vial", false);
        Domain specimenDomain = specimenTablesProvider.getDomain("specimen", false);

        if (null == eventProps && null != eventDomain)
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
        CaseInsensitiveHashSet eventFieldNamesDisallowedForRollups = SpecimenImporter.getEventFieldNamesDisallowedForRollups();
        Map<String, Pair<String, SpecimenImporter.RollupInstance<SpecimenImporter.EventVialRollup>>> vialToEventNameMap = SpecimenImporter.getVialToEventNameMap(vialProps, eventProps);     // includes rollups with type mismatches

        if (null != vialProps)
        {
            for (PropertyDescriptor prop : vialProps)
            {
                Pair<String, SpecimenImporter.RollupInstance<SpecimenImporter.EventVialRollup>> eventPair = vialToEventNameMap.get(prop.getName().toLowerCase());
                if (null != eventPair)
                {
                    String eventFieldName = eventPair.first;
                    if (eventFieldNamesDisallowedForRollups.contains(eventFieldName))
                        errors.add("You may not rollup from SpecimenEvent field '" + eventFieldName + "'.");
                    else if (!eventPair.second.isTypeConstraintMet())
                        errors.add("SpecimenEvent field '" + eventFieldName + "' would rollup to '" + prop.getName() + "' except the type constraint is not met.");
                }
                else
                    warnings.add("Vial field '" + prop.getName() + "' has no SpecimenEvent field that will rollup to it.");
            }
        }

        if (null != vialDomain)
        {   // Consider that rollups can come from base properties
            for (DomainProperty domainProperty : vialDomain.getBaseProperties())
                vialProps.add(domainProperty.getPropertyDescriptor());
        }
        CaseInsensitiveHashSet vialFieldNamesDisallowedForRollups = SpecimenImporter.getVialFieldNamesDisallowedForRollups();
        Map<String, Pair<String, SpecimenImporter.RollupInstance<SpecimenImporter.VialSpecimenRollup>>> specimenToVialNameMap = SpecimenImporter.getSpecimenToVialNameMap(specimenProps, vialProps);     // includes rollups with type mismatches

        if (null != specimenProps)
        {
            for (PropertyDescriptor prop : specimenProps)
            {
                Pair<String, SpecimenImporter.RollupInstance<SpecimenImporter.VialSpecimenRollup>> vialPair = specimenToVialNameMap.get(prop.getName().toLowerCase());
                if (null != vialPair)
                {
                    String vialFieldName = vialPair.first;
                    if (vialFieldNamesDisallowedForRollups.contains(vialFieldName))
                        errors.add("You may not rollup from Vial field '" + vialFieldName + "'.");
                    else if (!vialPair.second.isTypeConstraintMet())
                        errors.add("Vial field '" + vialFieldName + "' would rollup to '" + prop.getName() + "' except the type constraint is not met.");
                }
                else
                    warnings.add("Specimen field '" + prop.getName() + "' has no Vial field that will rollup to it.");
            }
        }

        SpecimenDomainRollupErrorsAndWarning result = new SpecimenDomainRollupErrorsAndWarning();
        result.setErrors(errors);
        result.setWarnings(warnings);
        return result;
    }

    private List<PropertyDescriptor> getPropertyDescriptorsForDomain(Domain domain, Container container)
    {
        List<PropertyDescriptor> pds = new ArrayList<>();
        for (DomainProperty prop : domain.getProperties())
        {
            if (null != prop.getName())
            {
                if(!prop.isRequired())
                {
                    pds.add(OntologyManager.getPropertyDescriptor(prop.getPropertyURI(), container));
                }
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

    protected static class SpecimenDomainRollupErrorsAndWarning
    {
        List<String> errors;
        List<String> warnings;

        public List<String> getErrors()
        {
            return errors;
        }

        public void setErrors(List<String> errors)
        {
            this.errors = errors;
        }

        public List<String> getWarnings()
        {
            return warnings;
        }

        public void setWarnings(List<String> warnings)
        {
            this.warnings = warnings;
        }
    }

}
