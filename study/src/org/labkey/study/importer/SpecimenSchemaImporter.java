package org.labkey.study.importer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.query.SpecimenTablesProvider;
import org.labkey.study.writer.SpecimenArchiveWriter;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 2/7/14.
 */
public class SpecimenSchemaImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "specimen schemas";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
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
        SpecimenTablesProvider tablesProvider = new SpecimenTablesProvider(ctx.getContainer(), ctx.getUser(), null);

        for (TableType tableXml : tablesXml.getTableArray())
        {
            final String tableName = tableXml.getTableName();

            // get the domain of the table we are updating
            final Domain domain = tablesProvider.getDomain(tableName, false);
            final Container container = ctx.getContainer();

            if (domain != null)
            {
                ImportTypesHelper importHelper = new ImportTypesHelper(tableXml, "TableName", tableName);
                List<Map<String, Object>> importMaps = importHelper.createImportMaps();
                List<String> propErrors = new ArrayList<>();

                // Create a map of existing properties
                Map<String, PropertyDescriptor> current = new CaseInsensitiveHashMap<>();
                for (DomainProperty dp : domain.getProperties())
                {
                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    current.put(pd.getName(), pd);
                }

                DomainURIFactory factory = new DomainURIFactory() {
                    @Override
                    public Pair<String, Container> getDomainURI(String name)
                    {
                        return new Pair<>(domain.getTypeURI(), container);
                    }
                };

                OntologyManager.ListImportPropertyDescriptors pds = OntologyManager.createPropertyDescriptors(factory, "TableName", importMaps, propErrors, container, true);
                if (!propErrors.isEmpty())
                    throw new ImportException("Unable to get an instance of TablesDocument from " + SpecimenArchiveWriter.SCHEMA_FILENAME);

                boolean isDirty = false;
                for (OntologyManager.ImportPropertyDescriptor ipd : pds.properties)
                {
                    if (!current.containsKey(ipd.pd.getName()))
                    {
                        domain.addPropertyOfPropertyDescriptor(ipd.pd);
                        isDirty = true;
                    }
                }

                for (Map.Entry<String, List<ConditionalFormat>> entry : pds.formats.entrySet())
                {
                    if (!current.containsKey(entry.getKey()))
                    {
                        PropertyService.get().saveConditionalFormats(ctx.getUser(), OntologyManager.getPropertyDescriptor(entry.getKey(), container), entry.getValue());
                        isDirty = true;
                    }
                }

                if (isDirty)
                    domain.save(ctx.getUser());
            }
        }
    }
}
