/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.reader.Readers;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.reportProps.PropertyList;
import org.labkey.study.model.DatasetDefinition;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: May 26, 2009
 * Time: 10:31:36 AM
 */
public class SchemaTsvReader implements SchemaReader
{
    private final Map<Integer, DatasetImportInfo> _datasetInfoMap;
    private final String _typeNameColumn;
    private List<ImportTypesHelper.Builder> _propertyBuilders = new ArrayList<>();


    private SchemaTsvReader(Study study, TabLoader loader, String labelColumn, String typeNameColumn, String typeIdColumn, Map<String, DatasetDefinitionImporter.DatasetImportProperties> extraImportProps, BindException errors) throws IOException
    {
        loader.setParseQuotes(true);
        List<Map<String, Object>> mapsLoad = loader.load();

        _datasetInfoMap = new HashMap<>();
        _typeNameColumn = typeNameColumn;

        if (mapsLoad.size() > 0)
        {
            int missingTypeNames = 0;
            int missingTypeIds = 0;
            int missingTypeLabels = 0;

            for (Map<String, Object> props : mapsLoad)
            {
                props = new CaseInsensitiveHashMap<>(props);

                String typeName = (String) props.get(typeNameColumn);
                Object typeIdObj = props.get(typeIdColumn);
                String propName = (String) props.get("Property");

                if (typeName == null || typeName.length() == 0)
                {
                    missingTypeNames++;
                    continue;
                }

                if (!(typeIdObj instanceof Integer))
                {
                    missingTypeIds++;
                    continue;
                }

                Integer typeId = (Integer) typeIdObj;
                DatasetDefinitionImporter.DatasetImportProperties extraProps = null != extraImportProps ? extraImportProps.get(typeName) : null;

                boolean isHidden;

                if (null != extraProps)
                {
                    isHidden = !extraProps.isShowByDefault();
                }
                else
                {
                    Object hidden = props.get("hidden");
                    if (null != hidden && !(hidden instanceof Boolean))
                        try {hidden = ConvertUtils.convert(hidden.toString(), Boolean.class);} catch (ConversionException x) {}
                    isHidden = (hidden instanceof Boolean && ((Boolean)hidden).booleanValue());
                }

                DatasetImportInfo info = _datasetInfoMap.get(typeId);

                if (info != null)
                {
                    if (!info.name.equals(typeName))
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " is associated with multiple type names ('" + typeName + "' and '" + info.name + "').");
                        return;
                    }
                    if (!info.isHidden == isHidden)
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " is set as both hidden and not hidden in different fields.");
                        return;
                    }
                }

                // we've got a good entry
                if (null == info)
                {
                    info = new DatasetImportInfo(typeName);
                    info.label = (String) props.get(labelColumn);
                    if (info.label == null || info.label.length() == 0)
                    {
                        missingTypeLabels++;
                        continue;
                    }

                    info.isHidden = isHidden;
                    _datasetInfoMap.put((Integer) typeIdObj, info);
                }

                // filter out the built-in types
                if (DatasetDefinition.isDefaultFieldName(propName, study))
                    continue;

                // look for visitdate column
                String conceptURI = (String)props.get("ConceptURI");
                if (null == conceptURI)
                {
                    String vtype = (String)props.get("vtype");  // datafax special case
                    if (null != vtype && vtype.toLowerCase().contains("visitdate"))
                        conceptURI = DatasetDefinition.getVisitDateURI();
                }

                if (DatasetDefinition.getVisitDateURI().equalsIgnoreCase(conceptURI))
                {
                    if (info.visitDatePropertyName == null)
                        info.visitDatePropertyName = propName;
                    else
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple visitdate fields (" + info.visitDatePropertyName + " and " + propName+").");
                        return;
                    }
                }

                // Deal with extra key field
                IntegerConverter intConverter = new IntegerConverter(0);
                Integer keyField = (Integer)intConverter.convert(Integer.class, props.get("key"));
                if (keyField != null && keyField.intValue() == 1)
                {
                    if (info.keyPropertyName == null)
                        info.keyPropertyName = propName;
                    else
                    {
                        // It's already been set
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple fields with key set to 1.");
                        return;
                    }
                }

                // Deal with managed key field
                String keyTypeName = props.get("AutoKey") == null ? null : props.get("AutoKey").toString();
                DatasetDefinition.KeyManagementType keyType = Dataset.KeyManagementType.findMatch(keyTypeName);
                if (keyType != Dataset.KeyManagementType.None)
                {
                    if (info.keyManagementType == Dataset.KeyManagementType.None)
                        info.keyManagementType = keyType;
                    else
                    {
                        // It's already been set
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple fields set to AutoKey.");
                        return;
                    }
                    // Check that our key is the key field as well
                    if (!propName.equals(info.keyPropertyName))
                    {
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " is set to AutoKey, but is not a key");
                        return;
                    }
                }

                // Category field
                String category = null != extraProps ? extraProps.getCategory() : (String)props.get("Category");

                if (category != null && !"".equals(category))
                {
                    if (info.category != null && !info.category.equalsIgnoreCase(category))
                    {
                        // It's changed from field to field within the same dataset
                        errors.reject("SchemaTsvReader", "Type ID " + typeName + " has multiple fields set with different categories");
                        return;
                    }
                    else
                    {
                        info.category = category;
                    }
                }

                // tags
                PropertyList tags = null != extraProps ? extraProps.getTags() : null;
                if (tags != null)
                    info.tags = tags;

                PropertyType pt = PropertyType.getFromURI(conceptURI, (String)props.get("RangeURI"), null);
                ImportTypesHelper.Builder pdb = new ImportTypesHelper.Builder(study.getContainer(), pt);
                pdb.setName(propName);
                pdb.setDomainName(typeName);
                pdb.setConceptURI(conceptURI);
                pdb.setMvEnabled(BooleanUtils.toBoolean(String.valueOf(props.get("MvEnabled"))));
                pdb.setLabel((String)props.get("Label"));
                pdb.setRequired(BooleanUtils.toBoolean(String.valueOf(props.get("Required"))));

                _propertyBuilders.add(pdb);
            }

            if (missingTypeNames > 0)
            {
                errors.reject("SchemaTsvReader", "Couldn't find type name in column " + typeNameColumn + " in " + missingTypeNames + " rows.");
                return;
            }

            if (missingTypeIds > 0)
            {
                errors.reject("SchemaTsvReader", "Couldn't find type id in column " + typeIdColumn + " in " + missingTypeIds + " rows.");
                return;
            }

            if (missingTypeLabels > 0)
            {
                errors.reject("SchemaTsvReader", "Couldn't find type label in column " + typeIdColumn + " in " + missingTypeLabels + " rows.");
                return;
            }
        }
    }

    public SchemaTsvReader(Study study, String tsv, String labelColumn, String typeNameColumn, String typeIdColumn, BindException errors) throws IOException
    {
        this(study, new TabLoader(tsv, true), labelColumn, typeNameColumn, typeIdColumn, null, errors);
    }

    public SchemaTsvReader(Study study, VirtualFile root, String tsvFileName, String labelColumn, String typeNameColumn, String typeIdColumn, Map<String, DatasetDefinitionImporter.DatasetImportProperties> extraImportProps, BindException errors) throws IOException
    {
        this(study, new TabLoader(Readers.getReader(root.getInputStream(tsvFileName)), true), labelColumn, typeNameColumn, typeIdColumn, extraImportProps, errors);
    }

    @Override
    public OntologyManager.ImportPropertyDescriptorsList getImportPropertyDescriptors(DomainURIFactory factory, Collection<String> errors, Container defaultContainer)
    {
        return ImportTypesHelper.getImportPropertyDescriptors(_propertyBuilders, factory, errors, defaultContainer);
    }

    public Map<Integer, DatasetImportInfo> getDatasetInfo()
    {
        return _datasetInfoMap;
    }

    public String getTypeNameColumn()
    {
        return _typeNameColumn;
    }
}
