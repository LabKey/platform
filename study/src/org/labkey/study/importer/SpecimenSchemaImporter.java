/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyManager.ImportPropertyDescriptorsList;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.model.DefaultSpecimenTablesTemplate;
import org.labkey.study.query.SpecimenTablesProvider;
import org.labkey.study.writer.SpecimenArchiveWriter;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 2/7/14.
 */
public class SpecimenSchemaImporter implements InternalStudyImporter
{
    SpecimenTablesTemplate _template = new ImportTemplate();

    @Override
    public String getDescription()
    {
        return "specimen schemas";
    }

    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.SPECIMENS;
    }

    public static boolean containsSchemasToImport(StudyImportContext ctx) throws ImportException
    {
        StudyDocument.Study.Specimens specimens = ctx.getXml().getSpecimens();
        if (null != specimens && null != specimens.getDir())
        {
            try {
                VirtualFile specimenDir = ctx.getRoot().getDir(specimens.getDir());
                if (null != specimenDir && null != specimens.getFile())
                {
                    XmlObject schemaXml = specimenDir.getXmlBean(SpecimenArchiveWriter.SCHEMA_FILENAME);
                    return (schemaXml instanceof TablesDocument);
                }
            }
            catch (IOException e)
            {
                throw new ImportException(e.getMessage());
            }
        }
        return false;
    }

    @Nullable
    public static VirtualFile getSpecimenFolder(StudyImportContext ctx) throws org.labkey.api.admin.ImportException
    {
        StudyDocument.Study.Specimens specimens = ctx.getXml().getSpecimens();
        if (null != specimens && null != specimens.getDir())
        {
            return ctx.getRoot().getDir(specimens.getDir());
        }
        return null;
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (!isValidForImportArchive(ctx, root))
            return;

        TablesDocument tablesDoc;
        try
        {
            XmlObject schemaXml = root.getXmlBean(SpecimenArchiveWriter.SCHEMA_FILENAME);
            if (schemaXml instanceof TablesDocument)
            {
                tablesDoc = (TablesDocument)schemaXml;
                XmlBeansUtil.validateXmlDocument(tablesDoc, SpecimenArchiveWriter.SCHEMA_FILENAME);
            }
            else
            {
                ctx.getLogger().info("No specimen metadata file found to import");
                return;
            }
        }
        catch (XmlValidationException e)
        {
            throw new InvalidFileException(SpecimenArchiveWriter.SCHEMA_FILENAME, e);
        }

        TablesType tablesXml = tablesDoc.getTables();
        SpecimenTablesProvider tablesProvider = new SpecimenTablesProvider(ctx.getContainer(), ctx.getUser(), _template);

        for (TableType tableXml : tablesXml.getTableArray())
        {
            final String tableName = tableXml.getTableName();

            // get the domain of the table we are updating
            final Domain domain = tablesProvider.getDomain(tableName, false);
            final Container container = ctx.getContainer();

            if (domain != null)
            {
                ImportTypesHelper importHelper = new ImportTypesHelper(tableXml, "TableName", tableName);
                List<String> propErrors = new ArrayList<>();

                // Create a map of existing properties
                Map<String, PropertyDescriptor> current = new CaseInsensitiveHashMap<>();
                for (DomainProperty dp : domain.getProperties())
                {
                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    current.put(pd.getName(), pd);
                }

                DomainURIFactory factory = name -> new Pair<>(domain.getTypeURI(), container);

                ImportPropertyDescriptorsList pds = importHelper.getImportPropertyDescriptors(factory, propErrors, container);
                if (!propErrors.isEmpty())
                    throw new ImportException("Unable to get an instance of TablesDocument from " + SpecimenArchiveWriter.SCHEMA_FILENAME);

                boolean isDirty = false;
                for (OntologyManager.ImportPropertyDescriptor ipd : pds.properties)
                {
                    if (!current.containsKey(ipd.pd.getName()))
                    {
                        DomainProperty domainProperty = domain.addPropertyOfPropertyDescriptor(ipd.pd);
                        ipd.validators.forEach(domainProperty::addValidator);
                        domainProperty.setConditionalFormats(ipd.formats);
                        isDirty = true;
                    }
                }

                if (isDirty)
                    domain.save(ctx.getUser());
            }
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        try
        {
            return root != null && root.getXmlBean(SpecimenArchiveWriter.SCHEMA_FILENAME) != null;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * A specimen tables template implementation that only provides the required (built in) fields but none of the optional
     * fields. This makes it easier to handle both additions and subtractions to the specimen domains on export/import.
     */
    public static class ImportTemplate extends DefaultSpecimenTablesTemplate implements SpecimenTablesTemplate
    {
        @Override
        public Set<PropertyStorageSpec> getExtraSpecimenEventProperties()
        {
            return Collections.emptySet();
        }

        @Override
        public Set<PropertyStorageSpec> getExtraVialProperties()
        {
            return Collections.emptySet();
        }

        @Override
        public Set<PropertyStorageSpec> getExtraSpecimenProperties()
        {
            return Collections.emptySet();
        }
    }
}
