/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.NotFoundException;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.domainTemplate.DataClassOptionsType;
import org.labkey.data.xml.domainTemplate.DataClassTemplateType;
import org.labkey.data.xml.domainTemplate.DomainTemplateType;
import org.labkey.data.xml.domainTemplate.EHRTemplateType;
import org.labkey.data.xml.domainTemplate.EHRBillingTemplateType;
import org.labkey.data.xml.domainTemplate.EHRLookupsTemplateType;
import org.labkey.data.xml.domainTemplate.IndexType;
import org.labkey.data.xml.domainTemplate.InitialDataType;
import org.labkey.data.xml.domainTemplate.ListOptionsType;
import org.labkey.data.xml.domainTemplate.ListTemplateType;
import org.labkey.data.xml.domainTemplate.SNDTemplateType;
import org.labkey.data.xml.domainTemplate.SampleSetOptionsType;
import org.labkey.data.xml.domainTemplate.SampleSetTemplateType;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User: kevink
 * Date: 1/6/16
 */
public class DomainTemplate
{
    private final String _moduleName;
    private final String _templateGroup;
    private final String _templateName;
    private final List<String> _errors;

    private final String _domainKind;
    private final GWTDomain _domain;
    private final Map<String, Object> _options;
    private final InitialDataSettings _initialData;

    /**
     * Find the DomainTemplate used to create this Domain, if it is available.
     */
    @Nullable
    public static DomainTemplate findTemplate(TemplateInfo info, String kindName)
    {
        if (info == null)
            return null;

        return findTemplate(info.getModuleName(), info.getTemplateGroupName(), info.getTableName(), kindName);
    }

    @Nullable
    public static DomainTemplate findTemplate(String moduleName, String groupName, String templateName, String kindName)
    {
        if (moduleName == null || groupName == null || templateName == null)
        {
            Logger.getLogger(DomainTemplate.class).warn("Module, group, and template name required to get domain template");
            return null;
        }

        Module module = ModuleLoader.getInstance().getModule(moduleName);
        if (module == null)
        {
            Logger.getLogger(DomainTemplate.class).warn("Module '" + moduleName + "' for domain template not found");
            return null;
        }

        DomainTemplateGroup group = DomainTemplateGroup.get(module, groupName);
        if (group == null)
        {
            Logger.getLogger(DomainTemplate.class).warn("Domain template group '" + groupName + "' not found in module '" + moduleName + "'");
            return null;
        }

        return group.getTemplate(templateName, kindName, false);
    }

    public static DomainTemplate parse(String moduleName, String groupName, DomainTemplateType template)
    {
        String templateName = template.getTable().getTableName();
        try
        {
            return _parse(templateName, moduleName, groupName, template);
        }
        catch (IllegalArgumentException ex)
        {
            return new DomainTemplate(Objects.toString(templateName, "<unknown>"), moduleName, groupName, Arrays.asList(ex.getMessage()));
        }
    }

    private static DomainTemplate _parse(String templateName, String moduleName, String groupName, DomainTemplateType template)
    {
        if (templateName == null)
            throw new IllegalArgumentException("template name required");

        List<GWTPropertyDescriptor> properties = getDomainTemplateProperties(templateName, template);

        String domainKind = getDomainKind(templateName, template, properties);
        if (domainKind == null)
            throw new IllegalArgumentException("Unknown template domain kind");

        List<GWTIndex> indices = getDomainTemplateUniqueIndices(templateName, template, properties);
        Set<String> mandatoryFieldNames = getDomainTemplateMandatoryFields(templateName, template, properties);
        Map<String, Object> options = getDomainTemplateOptions(templateName, template, null, properties);

        GWTDomain<GWTPropertyDescriptor> domain = new GWTDomain<>();
        domain.setName(templateName);
        domain.setDescription(template.getTable().getDescription());
        domain.setFields(properties);
        domain.setIndices(indices);
        domain.setMandatoryFieldNames(mandatoryFieldNames);

        InitialDataSettings importData = getImportDataSettings(templateName, template);

        return new DomainTemplate(
                templateName, groupName, moduleName,
                domainKind, domain, options,
                importData
        );
    }

    private static String getDomainKind(String templateName, DomainTemplateType template, List<GWTPropertyDescriptor> properties)
    {
        return (template instanceof ListTemplateType) ? getListDomainKind(templateName, (ListTemplateType)template, properties) :
               (template instanceof SampleSetTemplateType) ? "SampleSet" :
               (template instanceof DataClassTemplateType) ? "DataClass" :
               (template instanceof SNDTemplateType) ? "SND" :
               (template instanceof EHRTemplateType) ? "EHR" :
               (template instanceof EHRBillingTemplateType) ? "EHR_Billing" :
               (template instanceof EHRLookupsTemplateType) ? "EHR_Lookups" :
                null;
    }

    private static String getListDomainKind(String templateName, ListTemplateType template, List<GWTPropertyDescriptor> properties)
    {
        ListOptionsType options = template.getOptions();
        if (options == null)
            throw new IllegalArgumentException("List template requires specifying a keyCol");

        String keyName = options.getKeyCol();
        if (keyName == null)
            throw new IllegalArgumentException("List template requires specifying a keyCol");

        Pair<GWTPropertyDescriptor, Integer> pair = findProperty(templateName, properties, keyName);
        GWTPropertyDescriptor prop = pair.first;

        PropertyType type = PropertyType.getFromURI(prop.getConceptURI(), prop.getRangeURI());
        if (type == PropertyType.INTEGER)
            return "IntList";
        else if (type == PropertyType.STRING)
            return "VarList";

        throw new IllegalArgumentException("List template key column must be either of integer or string type");
    }

    private static List<GWTPropertyDescriptor> getDomainTemplateProperties(String templateName, DomainTemplateType template)
    {
        Map<String, GWTPropertyDescriptor> properties = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());

        TableType.Columns columns = template.getTable().getColumns();
        if (null != columns)
        {
            for (ColumnType columnType : columns.getColumnArray())
            {
                GWTPropertyDescriptor pd = DomainUtil.getPropertyDescriptor(columnType);
                if (null != properties.put(pd.getName(), pd))
                    throw new IllegalArgumentException("Duplicate column name '" + pd.getName() + "' in template '" + templateName + "'");
            }
        }

        return Collections.unmodifiableList(new ArrayList<>(properties.values()));
    }

    private static List<GWTIndex> getDomainTemplateUniqueIndices(String templateName, DomainTemplateType template, List<GWTPropertyDescriptor> properties)
    {
        List<GWTIndex> indices = new ArrayList<>();

        if (template.isSetIndices())
        {
            for (IndexType index : template.getIndices().getIndexArray())
            {
                // Only unique is supported currently
                if (index.getType() != null && TableInfo.IndexType.Unique.name().equalsIgnoreCase(index.getType().toString()))
                {
                    List<String> colNames = new ArrayList<>(4);
                    for (String colName : index.getColumnArray())
                    {
                        // ensure the column exists or throw an Exception
                        Pair<GWTPropertyDescriptor, Integer> pair = findProperty(templateName, properties, colName);
                        colNames.add(colName);
                    }
                    indices.add(new GWTIndex(Collections.unmodifiableList(colNames), true));
                }
            }
        }

        return Collections.unmodifiableList(indices);
    }

    private static Set<String> getDomainTemplateMandatoryFields(String templateName, DomainTemplateType template, List<GWTPropertyDescriptor> properties)
    {
        CaseInsensitiveHashSet set = new CaseInsensitiveHashSet();

        ColumnType[] columns = null;
        TableType tableType = template.getTable();
        if (tableType != null && tableType.isSetColumns())
            columns = template.getTable().getColumns().getColumnArray();

        if (columns != null)
        {
            for (ColumnType column : columns)
            {
                // look for a "mandatory" attribute in the no-namespace
                XmlObject xobj = column.selectAttribute(null, "mandatory");
                if (xobj != null)
                {
                    XmlCursor cur = null;
                    try
                    {
                        cur = xobj.newCursor();
                        if ("true".equalsIgnoreCase(cur.getTextValue()))
                            set.add(column.getColumnName());
                    }
                    catch (Exception e)
                    {
                        if (cur != null) cur.dispose();
                    }
                }
            }
        }

        return Collections.unmodifiableSet(set);
    }

    private static Map<String, Object> getDomainTemplateOptions(String templateName, DomainTemplateType template, Container container, List<GWTPropertyDescriptor> properties)
    {
        Map<String, Object> optionsMap = new HashMap<>();

        if (template instanceof ListTemplateType)
        {
            ListOptionsType options = ((ListTemplateType)template).getOptions();
            String keyName = options.getKeyCol();
            optionsMap.put("keyName", keyName);
        }
        else if (template instanceof DataClassTemplateType)
        {
            DataClassOptionsType options = ((DataClassTemplateType)template).getOptions();
            if (options != null)
            {
                optionsMap.put("nameExpression", options.getNameExpression());
                if (options.isSetSampleSet())
                    optionsMap.put("sampleSet", options.getSampleSet());
            }
        }
        else if (template instanceof SampleSetTemplateType)
        {
            SampleSetOptionsType options = ((SampleSetTemplateType)template).getOptions();
            if (options != null)
            {
                optionsMap.put("nameExpression", options.getNameExpression());

                String[] keyCols = options.getKeyColArray();
                Integer[] idCols = new Integer[keyCols.length];
                for (int i = 0; i < keyCols.length; i++)
                {
                    String keyCol = keyCols[i];
                    Pair<GWTPropertyDescriptor, Integer> pair = findProperty(templateName, properties, keyCol);
                    idCols[i] = pair.second;
                }
                optionsMap.put("idCols", idCols);
            }
        }

        return Collections.unmodifiableMap(optionsMap);
    }

    private static Pair<GWTPropertyDescriptor, Integer> findProperty(String templateName, List<GWTPropertyDescriptor> properties, String col)
    {
        for (int i = 0; i < properties.size(); i++)
        {
            GWTPropertyDescriptor pd = properties.get(i);
            if (col.equalsIgnoreCase(pd.getName()))
                return Pair.of(pd, i);
        }
        throw new IllegalArgumentException("Failed to find keyCol '" + col + "' for template '" + templateName + "'");
    }

    @Nullable
    private static InitialDataSettings getImportDataSettings(String templateName, DomainTemplateType template)
    {
        if (!template.isSetInitialData())
            return null;

        InitialDataType data = template.getInitialData();
        String file = data.getFile();
        if (file == null)
            throw new IllegalArgumentException("File required in initial data settings for template '" + templateName + "'");

        boolean hasColumnHeaders = data.isSetHasColumnHeaders() ? data.getHasColumnHeaders() : true;
        return new InitialDataSettings(file, hasColumnHeaders, data.getImportIdentity(), data.getImportLookupByAlternateKey());
    }

    private DomainTemplate(@NotNull String name, @NotNull String groupName, @NotNull String moduleName,
                           @NotNull String domainKind, @NotNull GWTDomain domain,
                           @NotNull Map<String, Object> options, @Nullable InitialDataSettings initialData)
    {
        _moduleName = moduleName;
        _templateGroup = groupName;
        _templateName = name;
        _domainKind = domainKind;
        _errors = null;
        _domain = domain;
        _options = options;
        _initialData = initialData;
    }

    private DomainTemplate(@NotNull String name, @NotNull String groupName, @NotNull String moduleName,
                           @NotNull List<String> errors)
    {
        _moduleName = moduleName;
        _templateGroup = groupName;
        _templateName = name;
        _errors = errors;
        _domainKind = null;
        _domain = null;
        _options = null;
        _initialData = null;
    }

    public boolean hasErrors()
    {
        return _errors != null && !_errors.isEmpty();
    }

    public List<String> getErrors()
    {
        return _errors != null ? _errors : Collections.emptyList();
    }

    public void throwErrors() throws BatchValidationException
    {
        if (_errors != null && !_errors.isEmpty())
            throw new BatchValidationException(new ValidationException(_errors.get(0)));
    }

    public Domain createAndImport(Container c, User u, @Nullable String domainName, boolean createDomain, boolean importData) throws BatchValidationException
    {
        throwErrors();

        Domain d = null;
        if (createDomain)
        {
            try (DbScope.Transaction tx = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                d = DomainUtil.createDomain(this, c, u, domainName);
                tx.commit();
            }
        }

        if (importData)
        {
            try (DbScope.Transaction tx = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                importData(domainName, c, u);
                tx.commit();
            }
        }

        return d;
    }

    public int importData(@Nullable String domainName, Container c, User u) throws BatchValidationException
    {
        return importData(domainName, _initialData, c, u);
    }

    public int importData(String domainName, String moduleName, String fileName, Container c, User u) throws BatchValidationException
    {
        InitialDataSettings initialData = new InitialDataSettings(moduleName, fileName);
        return importData(domainName, initialData, c, u);
    }

    public int importData(@Nullable String domainName, InitialDataSettings initialData, Container c, User u) throws BatchValidationException
    {
        throwErrors();

        if (domainName == null)
            domainName = getDomain().getName();

        BatchValidationException errors = new BatchValidationException();
        int count = importData(domainName, initialData, c, u, errors);
        if (errors.hasErrors())
            throw errors;

        return count;
    }

    private int importData(String domainName, InitialDataSettings initialData, Container c, User u, BatchValidationException errors)
    {
        if (initialData == null)
            return 0;

        if (initialData.file == null)
            throw new IllegalStateException();

        DomainKind kind = PropertyService.get().getDomainKindByName(getDomainKind());
        if (kind == null)
            throw new IllegalArgumentException("No domain kind matches name '" + getDomainKind() + "'");

        // NOTE: Unfortunately, there currently is no direct mapping from domain->table.
        // NOTE: Getting the TableInfo for the Domain only works for some domain kinds.
        TableInfo table = kind.getTableInfo(u, c, domainName);
        if (table == null)
            throw new IllegalStateException();

        QueryUpdateService updateService = table.getUpdateService();
        if (updateService == null)
            throw new IllegalStateException();

        DataLoader dl;
        try
        {
            String moduleName = initialData.moduleName != null ? initialData.moduleName : _moduleName;
            dl = createDataLoader(moduleName, initialData.file, initialData.hasColumnHeaders);
        }
        catch (IOException e)
        {
            errors.addRowError(new ValidationException(e.getMessage()));
            return 0;
        }


        DataIteratorContext context = new DataIteratorContext(errors);
        context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
        context.setAllowImportLookupByAlternateKey(initialData.importLookupByAlternateKey);
        if (initialData.importIdentity)
        {
            context.setInsertOption(QueryUpdateService.InsertOption.IMPORT_IDENTITY);
            context.setSupportAutoIncrementKey(true);
        }

        try (DbScope.Transaction transaction = table.getSchema().getScope().ensureTransaction())
        {
            int count = updateService.loadRows(u, c, dl, context, new HashMap<>());
            if (errors.hasErrors())
                return 0;
            transaction.commit();
            return count;
        }
        catch (SQLException x)
        {
            boolean isConstraint = RuntimeSQLException.isConstraintException(x);
            if (isConstraint)
                errors.addRowError(new ValidationException(x.getMessage()));
            else
                throw new RuntimeSQLException(x);
        }

        return 0;
    }

    private static DataLoader createDataLoader(String moduleName, String path, boolean hasColumnHeaders) throws IOException
    {
        Module m = ModuleLoader.getInstance().getModule(moduleName);
        if (m == null)
            throw new NotFoundException("module not found '" + moduleName + "'");

        Path p = Path.parse(path).normalize();
        Resource r = m.getModuleResource(p);
        if (r == null || !r.isFile())
            throw new NotFoundException("Domain template data file '" + path + "' not found in module '" + moduleName + "'");

        return DataLoader.get().createLoader(r, hasColumnHeaders, null, TabLoader.TSV_FILE_TYPE);
    }

    public String getTemplateGroupKey()
    {
        Module m = ModuleLoader.getInstance().getModule(_moduleName);
        return ModuleResourceCaches.createCacheKey(m, _templateGroup);
    }

    public String getTemplateKey()
    {
        return getTemplateGroupKey() + ":" + _templateName;
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public String getTemplateGroup()
    {
        return _templateGroup;
    }

    public String getTemplateName()
    {
        return _templateName;
    }

    public String getDomainKind()
    {
        return _domainKind;
    }

    public GWTDomain getDomain()
    {
        return _domain;
    }

    public Map<String, Object> getOptions()
    {
        return _options;
    }

    public Set<String> getMandatoryPropertyNames()
    {
        return _domain.getMandatoryFieldNames();
    }


    private static class InitialDataSettings
    {
        String file;
        String moduleName;
        boolean hasColumnHeaders;
        boolean importIdentity;
        boolean importLookupByAlternateKey;

        public InitialDataSettings(String file, boolean hasColumnHeaders, boolean importIdentity, boolean importLookupByAlternateKey)
        {
            this.file = file;
            this.hasColumnHeaders = hasColumnHeaders;
            this.importIdentity = importIdentity;
            this.importLookupByAlternateKey = importLookupByAlternateKey;
        }

        public InitialDataSettings(String moduleName, String file)
        {
            this(file, true, false, false);
            this.moduleName = moduleName;
        }
    }

    public TemplateInfo getTemplateInfo()
    {
        Module m = ModuleLoader.getInstance().getModule(getModuleName());
        return new TemplateInfo(m.getName(), getTemplateGroup(), getTemplateName(), m.getVersion());
    }
}
