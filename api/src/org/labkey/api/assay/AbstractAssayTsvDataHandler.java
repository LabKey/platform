/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AssayResultsFileConverter;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.qc.ValidationDataHandler;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.ParticipantVisitResolver;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.jdbc.BadSqlGrammarException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static org.labkey.api.exp.OntologyManager.NO_OP_ROW_CALLBACK;
import static org.labkey.api.gwt.client.ui.PropertyType.SAMPLE_CONCEPT_URI;

public abstract class AbstractAssayTsvDataHandler extends AbstractExperimentDataHandler implements ValidationDataHandler
{
    protected static final Object ERROR_VALUE = new Object() {
        @Override
        public String toString()
        {
            return "{AbstractAssayTsvDataHandler.ERROR_VALUE}";
        }
    };

    private static final Logger LOG = LogHelper.getLogger(AbstractAssayTsvDataHandler.class, "Info related to assay data import");

    protected abstract boolean allowEmptyData();

    @Override
    public void importFile(@NotNull ExpData data, File dataFile, @NotNull ViewBackgroundInfo info, @NotNull Logger log, @NotNull XarContext context) throws ExperimentException
    {
        importFile(data, dataFile, info, log, context, true);
    }

    @Override
    public void importFile(@NotNull ExpData data, File dataFile, @NotNull ViewBackgroundInfo info, @NotNull Logger log, @NotNull XarContext context, boolean allowLookupByAlternateKey) throws ExperimentException
    {
        importFile(data, dataFile, info, log, context, allowLookupByAlternateKey, false);
    }

    @Override
    public void importFile(@NotNull ExpData data, File dataFile, @NotNull ViewBackgroundInfo info, @NotNull Logger log, @NotNull XarContext context, boolean allowLookupByAlternateKey, boolean autoFillDefaultResultColumns) throws ExperimentException
    {
        ExpProtocolApplication sourceApplication = data.getSourceApplication();
        if (sourceApplication == null)
        {
            throw new ExperimentException("Cannot import a TSV without knowing its assay definition");
        }
        ExpRun run = sourceApplication.getRun();
        ExpProtocol protocol = run.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        DataLoaderSettings settings = new DataLoaderSettings();
        settings.setAllowLookupByAlternateKey(allowLookupByAlternateKey);

        // Issue 48243: For the assay import of lookup columns, we want to allow for the original value to be used when
        // attempting to resolve the lookup (for example, the SampleId column might be type int but the original value
        // could be a string or double, and we want that original value to have a chance to resolve instead of giving a
        // type conversion error).
        settings.setBestEffortConversion(true);

        Map<DataType, DataIteratorBuilder> rawData = getValidationDataMap(data, dataFile, info, log, context, settings);
        assert(rawData.size() <= 1);
        try
        {
            importRows(data, info.getUser(), run, protocol, provider, rawData.values().iterator().next(), settings, autoFillDefaultResultColumns);
        }
        catch (BatchValidationException e)
        {
            throw new ExperimentException(e.toString(), e);
        }
    }

    public void importTransformDataMap(ExpData data, AssayRunUploadContext<?> context, ExpRun run, DataIteratorBuilder dataMap) throws ExperimentException
    {
        try
        {
            DataLoaderSettings settings = new DataLoaderSettings();
            importRows(data, context.getUser(), run, context.getProtocol(), context.getProvider(), dataMap, settings, context.shouldAutoFillDefaultResultColumns());
        }
        catch (BatchValidationException e)
        {
            throw new ExperimentException(e.toString(), e);
        }
    }

    @Override
    public Map<DataType, DataIteratorBuilder> getValidationDataMap(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context, DataLoaderSettings settings) throws ExperimentException
    {
        ExpProtocol protocol = data.getRun().getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain dataDomain = provider.getResultsDomain(protocol);
        boolean plateMetadataEnabled = provider.isPlateMetadataEnabled(protocol);

        try (DataLoader loader = createLoaderForImport(dataFile, data.getRun(), dataDomain, settings, true))
        {
            Map<DataType, DataIteratorBuilder> datas = new HashMap<>();
            DataIteratorBuilder dataRows = (diContext) -> loader.getDataIterator(diContext);

            if (plateMetadataEnabled && AssayPlateMetadataService.isExperimentalAppPlateEnabled())
            {
                Integer plateSetId = getPlateSetValueFromRunProps(context, provider, protocol);
                dataRows = AssayPlateMetadataService.get().parsePlateData(context.getContainer(), context.getUser(), provider, protocol, plateSetId, dataFile, dataRows);
            }

            // assays with plate metadata support will merge the plate metadata with the data rows to make it easier for
            // transform scripts to perform metadata related calculations
            if (plateMetadataEnabled)
                dataRows = mergePlateMetadata(context, provider, protocol, dataRows);

            datas.put(getDataType(), dataRows);

            return datas;
        }
    }

    private DataIteratorBuilder mergePlateMetadata(XarContext context, AssayProvider provider, ExpProtocol protocol, DataIteratorBuilder dataRows)
            throws ExperimentException
    {
        Domain runDomain = provider.getRunDomain(protocol);
        DomainProperty propertyPlateSet = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME);
        if (propertyPlateSet != null)
        {
            Integer plateSetId = getPlateSetValueFromRunProps(context, provider, protocol);
            return AssayPlateMetadataService.get().mergePlateMetadata(context.getContainer(), context.getUser(), plateSetId, dataRows, provider, protocol);
        }

        return dataRows;
    }

    @Nullable
    private Integer getPlateSetValueFromRunProps(XarContext context, AssayProvider provider, ExpProtocol protocol) throws ExperimentException
    {
        Domain runDomain = provider.getRunDomain(protocol);
        DomainProperty propertyPlateSet = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME);
        if (AssayPlateMetadataService.isExperimentalAppPlateEnabled() && propertyPlateSet == null)
        {
            throw new ExperimentException("The assay run domain for the assay '" + protocol.getName() + "' does not contain a plate set property.");
        }

        Map<DomainProperty, String> runProps = ((AssayUploadXarContext)context).getContext().getRunProperties();
        Object plateSetVal = runProps.getOrDefault(propertyPlateSet, null);
        return plateSetVal != null ? Integer.parseInt(String.valueOf(plateSetVal)) : null;
    }

    /**
     * Creates a DataLoader that can handle missing value indicators if the columns on the domain
     * are configured to support it.
     */
    public static DataLoader createLoaderForImport(File dataFile, ExpRun run, @Nullable Domain dataDomain, DataLoaderSettings settings, boolean shouldInferTypes)
    {
        Map<String, DomainProperty> aliases = new HashMap<>();
        Set<String> mvEnabledColumns = Sets.newCaseInsensitiveHashSet();
        Set<String> mvIndicatorColumns = Sets.newCaseInsensitiveHashSet();

        if (dataDomain != null)
        {
            List<? extends DomainProperty> columns = dataDomain.getProperties();
            aliases = dataDomain.createImportMap(false);
            for (DomainProperty col : columns)
            {
                if (col.isMvEnabled())
                {
                    // Check for all possible names for the column in the incoming data when deciding if we should
                    // check it for missing values
                    Set<String> columnAliases = ImportAliasable.Helper.createImportMap(Collections.singletonList(col), false).keySet();
                    mvEnabledColumns.addAll(columnAliases);
                    mvIndicatorColumns.add(col.getName() + MvColumn.MV_INDICATOR_SUFFIX);
                }
            }
        }

        try (DataLoader loader = DataLoader.get().createLoader(dataFile, null, true, null, TabLoader.TSV_FILE_TYPE))
        {
            loader.setThrowOnErrors(settings.isThrowOnErrors());
            loader.setInferTypes(shouldInferTypes);

            for (ColumnDescriptor column : loader.getColumns())
            {
                if (dataDomain != null)
                {
                    if (mvEnabledColumns.contains(column.name))
                    {
                        column.setMvEnabled(dataDomain.getContainer());
                    }
                    else if (mvIndicatorColumns.contains(column.name))
                    {
                        column.setMvIndicator(dataDomain.getContainer());
                        column.clazz = String.class;
                    }
                    DomainProperty prop = aliases.get(column.name);
                    if (prop != null)
                    {
                        // Allow String values through if the column is a lookup and the settings allow lookups by alternate key.
                        // The lookup table unique indices or display column value will be used to convert the column to the lookup value.
                        if (!(settings.isAllowLookupByAlternateKey() && column.clazz == String.class && prop.getLookup() != null))
                        {
                            // Otherwise, just use the expected PropertyDescriptor's column type
                            column.clazz = prop.getPropertyDescriptor().getPropertyType().getJavaType();
                        }
                    }
                    else
                    {
                        // It's not an expected column. Is it an MV indicator column or prov:objectInput column?
                        if (!settings.isAllowUnexpectedColumns() &&
                                !mvIndicatorColumns.contains(column.name) &&
                                !column.name.equalsIgnoreCase(ProvenanceService.PROVENANCE_INPUT_PROPERTY))
                        {
                            column.load = false;
                        }
                    }
                }

                if (settings.isBestEffortConversion())
                    column.errorValues = DataLoader.ERROR_VALUE_USE_ORIGINAL;
                else
                    column.errorValues = ERROR_VALUE;

                if (run != null && column.clazz == File.class)
                    column.converter = new AssayResultsFileConverter(run);
            }
            return loader;

        }
        catch (IOException ioe)
        {
            throw UnexpectedException.wrap(ioe);
        }
    }

    @Override
    public void beforeDeleteData(List<ExpData> data, User user) throws ExperimentException
    {
        // Group data by source run
        Map<ExpRun, List<ExpData>> grouping = new HashMap<>();
        for (ExpData d : data)
        {
            ExpProtocolApplication sourceApplication = d.getSourceApplication();
            if (sourceApplication != null)
            {
                ExpRun run = sourceApplication.getRun();
                if (run != null)
                {
                    var dataForRun = grouping.computeIfAbsent(run, x -> new ArrayList<>());
                    dataForRun.add(d);
                }
            }
        }

        for (Map.Entry<ExpRun, List<ExpData>> entry : grouping.entrySet())
        {
            ExpRun run = entry.getKey();
            List<ExpData> dataForRun = entry.getValue();
            List<Integer> dataIds = dataForRun.stream().map(ExpData::getRowId).collect(toList());

            ExpProtocol protocol = run.getProtocol();
            AssayProvider provider = AssayService.get().getProvider(protocol);
            FieldKey assayResultLsidFieldKey = provider != null ? provider.getTableMetadata(protocol).getResultLsidFieldKey() : null;

            SQLFragment assayResultLsidSql = null;

            Domain domain;
            if (provider != null && assayResultLsidFieldKey != null)
            {
                domain = provider.getResultsDomain(protocol);

                AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
                TableInfo assayDataTable = assayProtocolSchema.createDataTable(ContainerFilter.EVERYTHING, false);
                if (assayDataTable != null)
                {
                    ColumnInfo lsidCol = assayDataTable.getColumn(assayResultLsidFieldKey);
                    ColumnInfo dataIdCol = assayDataTable.getColumn("DataId");
                    if (lsidCol == null || dataIdCol == null)
                        throw new IllegalStateException("Assay results table expected to have dataId lookup column and " + assayResultLsidFieldKey + " column");

                    // select the assay results LSID column for all rows referenced by the data
                    assayResultLsidSql = new SQLFragment("SELECT ").append(lsidCol.getValueSql("X")).append(" AS ObjectURI")
                            .append(" FROM ").append(assayDataTable.getFromSQL("X"))
                            .append(" WHERE ").append(dataIdCol.getValueSql("X")).appendInClause(dataIds, assayDataTable.getSqlDialect());
                }
            }
            else
            {
                // Be tolerant of the AssayProvider no longer being available. See if we have the default
                // results/data domain for TSV-style assays
                try
                {
                    domain = AbstractAssayProvider.getDomainByPrefixIfExists(protocol, ExpProtocol.ASSAY_DOMAIN_DATA) ;
                }
                catch (IllegalStateException ignored)
                {
                    domain = null;
                    // Be tolerant of not finding a domain anymore, if the provider has gone away
                }

                // TODO: create assayResultLsidSql when provider no longer exists
            }

            // delete the assay result row exp.objects
            if (assayResultLsidSql != null)
            {
                if (LOG.isDebugEnabled())
                {
                    SQLFragment t = new SQLFragment("SELECT o.*")
                            .append(" FROM ").append(OntologyManager.getTinfoObject(), "o")
                            .append(" WHERE Container = ?").add(run.getContainer())
                            .append(" AND ObjectURI IN (")
                            .append(assayResultLsidSql)
                            .append(")");
                    SqlSelector ss = new SqlSelector(ExperimentService.get().getSchema(), t);
                    try (TableResultSet rs = ss.getResultSet())
                    {
                        LOG.debug("objects that will be deleted:");
                        ResultSetUtil.logData(rs, LOG);
                    }
                    catch (SQLException x)
                    {
                        throw new RuntimeSQLException(x);
                    }
                }

                // delete provenance for assay result rows
                ProvenanceService.get().deleteProvenanceByLsids(run.getContainer(), user, new SQLFragment(" IN (").append(assayResultLsidSql).append(")"), true, Set.of(StudyPublishService.STUDY_PUBLISH_PROTOCOL_LSID));

                int count = OntologyManager.deleteOntologyObjects(ExperimentService.get().getSchema(), assayResultLsidSql, run.getContainer());
                LOG.debug("AbstractAssayTsvDataHandler.beforeDeleteData: deleted " + count + " ontology objects for assay result lsids");
            }

            if (domain != null && domain.getStorageTableName() != null)
            {
                SQLFragment deleteSQL = new SQLFragment("DELETE FROM ");
                deleteSQL.append(domain.getDomainKind().getStorageSchemaName());
                deleteSQL.append(".");
                deleteSQL.append(domain.getStorageTableName());
                deleteSQL.append(" WHERE DataId ").appendInClause(dataIds, ExperimentService.get().getSchema().getSqlDialect());

                try
                {
                    int count = new SqlExecutor(DbSchema.get(domain.getDomainKind().getStorageSchemaName(), DbSchemaType.Provisioned)).execute(deleteSQL);
                    LOG.debug("AbstractAssayTsvDataHandler.beforeDeleteData: deleted " + count + " assay result rows");
                }
                catch (BadSqlGrammarException x)
                {
                    // (18035) presumably this is an optimistic concurrency problem and the table is gone
                    // postgres returns 42P01 in this case... SQL Server?
                    if (SqlDialect.isObjectNotFoundException(x))
                    {
                        // CONSIDER: unfortunately we can't swallow this exception, because Postgres leaves
                        // the connection in an unusable state
                    }
                    throw x;
                }
            }
        }
    }

    public void importRows(ExpData data, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, DataIteratorBuilder rawData, @Nullable DataLoaderSettings settings, boolean autoFillDefaultResultColumns)
            throws ExperimentException, BatchValidationException
    {
        if (settings == null)
            settings = new DataLoaderSettings();

        Domain dataDomain = provider.getResultsDomain(protocol);

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction();
             DataIterator rawIter = rawData.getDataIterator(new DataIteratorContext()))
        {
            DataIterator iter = new WrapperDataIterator(rawIter)
            {
                boolean foundRow = false;
                @Override
                public boolean next() throws BatchValidationException
                {
                    boolean result = super.next();
                    foundRow = foundRow || result;
                    if (!foundRow)
                    {
                        throw new NoRowsException();
                    }
                    return result;
                }
            };

            Container container = data.getContainer();
            ParticipantVisitResolver resolver = createResolver(user, run, protocol, provider, container);

            final ContainerFilter cf = QueryService.get().getContainerFilterForLookups(container, user);
            final TableInfo dataTable = provider.createProtocolSchema(user, container, protocol, null).createDataTable(cf);

            Map<String, ExpMaterial> protocolInputMaterials = new HashMap<>();
            List<? extends ExpProtocolApplication> protocolApplications = run.getProtocolApplications();
            if (protocolApplications != null)
            {
                for (ExpProtocolApplication protocolApplication : protocolApplications)
                {
                    for (ExpMaterial material : protocolApplication.getInputMaterials())
                        protocolInputMaterials.put(material.getName(), material);
                }
            }
            Map<ExpMaterial, String> rowBasedInputMaterials = new LinkedHashMap<>();

            DataIterator fileData = checkData(container, user, dataTable, dataDomain, iter, settings, resolver, protocolInputMaterials, cf, rowBasedInputMaterials);
            fileData = convertPropertyNamesToURIs(fileData, dataDomain);

            OntologyManager.RowCallback rowCallback = NO_OP_ROW_CALLBACK;
            ProvenanceService pvs = ProvenanceService.get();
            if (pvs.isProvenanceSupported() || provider.isPlateMetadataEnabled(protocol))
            {
                if (provider.getResultRowLSIDPrefix() == null)
                {
                    LOG.info("Import failed for run '" + run.getName() + "; Assay provider '" + provider.getName() + "' for assay '" + protocol.getName() + "' has no result row lsid prefix");
                    return;
                }

                // Attach run's final protocol application with output LSIDs for Assay Result rows
                rowCallback = rowCallback.chain(pvs.getAssayRowCallback(run, container));
            }

            // Insert the data into the assay's data table.
            // On insert, the raw data will have the provisioned table's rowId added to the list of maps
            // autoFillDefaultResultColumns - only populate created/modified/by for results created separately from runs
            insertRowData(data, user, container, run, protocol, provider, dataDomain, fileData, dataTable, autoFillDefaultResultColumns, rowCallback);

            SampleTypeService sampleService = SampleTypeService.get();
            Collection<? extends ExpMaterial> lockedSamples = sampleService.getSamplesNotPermitted(rowBasedInputMaterials.keySet(), SampleTypeService.SampleOperations.AddAssayData);
            if (!lockedSamples.isEmpty())
                throw new ExperimentException(sampleService.getOperationNotPermittedMessage(lockedSamples, SampleTypeService.SampleOperations.AddAssayData));

            if (shouldAddInputMaterials())
            {
                AbstractAssayProvider.addInputMaterials(run, user, rowBasedInputMaterials);
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        catch (NoRowsException e)
        {
            if (!allowEmptyData() && !dataDomain.getProperties().isEmpty())
            {
                throw new ExperimentException("Data file contained zero data rows");
            }
        }
    }

    protected ParticipantVisitResolver createResolver(User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, Container container)
            throws IOException, ExperimentException
    {
        return AssayService.get().createResolver(user, run, protocol, provider, null);
    }

    /** Insert the data into the database. Transaction is active. */
    protected void insertRowData(
        ExpData data,
        User user,
        Container container,
        ExpRun run,
        ExpProtocol protocol,
        AssayProvider provider,
        Domain dataDomain,
        DataIterator fileData,
        TableInfo tableInfo,
        boolean autoFillDefaultColumns,
        OntologyManager.RowCallback rowCallback
    ) throws SQLException, BatchValidationException, ExperimentException
    {
        OntologyManager.UpdateableTableImportHelper importHelper = new SimpleAssayDataImportHelper(data, protocol, provider);
        if (provider.isPlateMetadataEnabled(protocol))
            importHelper = AssayPlateMetadataService.get().getImportHelper(container, user, run, data, protocol, provider);

        if (tableInfo instanceof UpdateableTableInfo)
        {
            OntologyManager.insertTabDelimited(tableInfo, container, user, importHelper, fileData, autoFillDefaultColumns, LOG, rowCallback);
        }
        else
        {
            Integer id = OntologyManager.ensureObject(container, data.getLSID());
            OntologyManager.insertTabDelimited(container, user, id, importHelper, dataDomain, fileData, false, rowCallback);
        }
    }

    /** Signals that an import found no row to import. In some cases this is allowed, in others it's an error condition. */
    private static class NoRowsException extends RuntimeException
    {
    }

    protected abstract boolean shouldAddInputMaterials();

    private void checkColumns(Domain dataDomain, DataIterator rawData) throws BatchValidationException
    {
        List<String> missing = new ArrayList<>();

        Set<String> checkSet = new CaseInsensitiveHashSet();
        List<? extends DomainProperty> expected = dataDomain.getProperties();
        for (DomainProperty pd : expected)
        {
            checkSet.add(pd.getName());
            if (pd.isMvEnabled())
                checkSet.add((pd.getName() + MvColumn.MV_INDICATOR_SUFFIX));
        }

        // Now figure out what's missing but required
        Map<String, DomainProperty> importMap = dataDomain.createImportMap(true);
        // Consider all of them initially
        LinkedHashSet<DomainProperty> missingProps = new LinkedHashSet<>(expected);

        // Iterate through the ones we got
        for (int i = 1; i <= rawData.getColumnCount(); i++)
        {
            String col = rawData.getColumnInfo(i).getName();
            // Find the property that it maps to (via name, label, import alias, etc)
            DomainProperty prop = importMap.get(col);
            if (prop != null)
            {
                // If there's a match, don't consider it missing anymore
                missingProps.remove(prop);
            }
        }

        for (DomainProperty pd : missingProps)
        {
            if ((pd.isRequired()))
                missing.add(pd.getName());
        }

        if (!missing.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            builder.append("Expected columns were not found: ");
            for (java.util.Iterator<String> it = missing.iterator(); it.hasNext(); )
            {
                builder.append(it.next());
                if (it.hasNext())
                    builder.append(", ");
                else
                    builder.append(".  ");
            }
            throw new BatchValidationException(new ValidationException(builder.toString()));
        }
    }

    /**
     * @param rowBasedInputMaterials the map of materials that are inputs to this run based on the data rows
     */
    private DataIterator checkData(Container container,
                                               User user,
                                               TableInfo dataTable,
                                               Domain dataDomain,
                                               DataIterator rawData,
                                               DataLoaderSettings settings,
                                               ParticipantVisitResolver resolver,
                                               Map<String, ExpMaterial> inputMaterials,
                                               ContainerFilter containerFilter,
                                               Map<ExpMaterial, String> rowBasedInputMaterials)
            throws BatchValidationException
    {
        final ExperimentService exp = ExperimentService.get();

        // For now, we'll only enforce that required columns are present.  In the future, we'd like to
        // do a strict check first, and then present ignorable warnings.
        checkColumns(dataDomain, rawData);

        DomainProperty participantPropFinder = null;
        DomainProperty specimenPropFinder = null;
        DomainProperty visitPropFinder = null;
        DomainProperty datePropFinder = null;
        DomainProperty targetStudyPropFinder = null;

        RemapCache cache = new RemapCache();
        Map<DomainProperty, TableInfo> remappableLookup = new HashMap<>();
        Map<Integer, ExpMaterial> materialCache = new HashMap<>();

        Map<DomainProperty, ExpSampleType> lookupToSampleTypeByName = new HashMap<>();
        Map<DomainProperty, ExpSampleType> lookupToSampleTypeById = new HashMap<>();
        Set<DomainProperty> lookupToAllSamplesByName = new HashSet<>();
        Set<DomainProperty> lookupToAllSamplesById = new HashSet<>();

        List<? extends DomainProperty> columns = dataDomain.getProperties();
        Map<DomainProperty, List<ColumnValidator>> validatorMap = new HashMap<>();

        for (DomainProperty pd : columns)
        {
            // initialize the DomainProperty validator map
            validatorMap.put(pd, ColumnValidators.create(null, pd));

            if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                participantPropFinder = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                specimenPropFinder = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.VISITID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.DOUBLE)
            {
                visitPropFinder = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.DATE_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.DATE_TIME)
            {
                datePropFinder = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                targetStudyPropFinder = pd;
            }
            else
            {
                ExpSampleType st = DefaultAssayRunCreator.getLookupSampleType(pd, container, user);
                if (st != null)
                {
                    if (pd.getPropertyType().getJdbcType().isText())
                    {
                        lookupToSampleTypeByName.put(pd, st);
                    }
                    else
                    {
                        lookupToSampleTypeById.put(pd, st);
                    }
                }
                else if (DefaultAssayRunCreator.isLookupToMaterials(pd))
                {
                    if (pd.getPropertyType().getJdbcType().isText())
                        lookupToAllSamplesByName.add(pd);
                    else
                        lookupToAllSamplesById.add(pd);
                }
            }

            if (dataTable != null && settings.isAllowLookupByAlternateKey())
            {
                ColumnInfo column = dataTable.getColumn(pd.getName());
                ForeignKey fk = column != null ? column.getFk() : null;
                if (fk != null && fk.allowImportByAlternateKey())
                {
                    remappableLookup.put(pd, fk.getLookupTableInfo());
                }
            }
        }

        boolean resolveMaterials = specimenPropFinder != null || visitPropFinder != null || datePropFinder != null || targetStudyPropFinder != null;

        Set<String> wrongTypes = new HashSet<>();

        Map<String, DomainProperty> aliasMap = dataDomain.createImportMap(true);

        // We want to share canonical casing between data rows, or we end up with an extra Map instance for each
        // data row which can add up quickly
        CaseInsensitiveHashMap<Object> caseMapping = new CaseInsensitiveHashMap<>();
        ValidatorContext validatorContext = new ValidatorContext(container, user);

        DomainProperty participantPD = participantPropFinder;
        DomainProperty specimenPD = specimenPropFinder;
        DomainProperty visitPD = visitPropFinder;
        DomainProperty datePD = datePropFinder;
        DomainProperty targetStudyPD = targetStudyPropFinder;

        BatchValidationException bve = new BatchValidationException();

        return DataIteratorUtil.mapTransformer(rawData, null, new Function<>()
        {
            int rowNum = 0;

            @Override
            public Map<String, Object> apply(Map<String, Object> originalMap)
            {
                rowNum++;
                Collection<ValidationError> errors = new ArrayList<>();
                Set<String> rowInputLSIDs = new HashSet<>();

                Map<String, Object> map = new CaseInsensitiveHashMap<>(caseMapping);
                // Rekey the map, resolving aliases to the actual property names
                for (Map.Entry<String, Object> entry : originalMap.entrySet())
                {
                    DomainProperty prop = aliasMap.get(entry.getKey());
                    if (prop != null)
                    {
                        map.put(prop.getName(), entry.getValue());
                    }
                    else if (entry.getKey().equalsIgnoreCase(ProvenanceService.PROVENANCE_INPUT_PROPERTY))
                    {
                        map.put(entry.getKey(), entry.getValue());
                    }
                }

                String participantID = null;
                String specimenID = null;
                Double visitID = null;
                Date date = null;
                Container targetStudy = null;

                for (DomainProperty pd : columns)
                {
                    Object o = map.get(pd.getName());
                    if (o instanceof String)
                    {
                        o = StringUtils.trimToNull((String) o);
                        map.put(pd.getName(), o);
                    }

                    // validate the data value for the non-sample lookup fields
                    // note that sample lookup mapping and validation will happen separately below in the code which handles populating materialInputs
                    boolean isSampleLookupById = lookupToAllSamplesById.contains(pd) || lookupToSampleTypeById.containsKey(pd);
                    boolean isSampleLookupByName = lookupToAllSamplesByName.contains(pd) || lookupToSampleTypeByName.containsKey(pd);
                    if (validatorMap.containsKey(pd) && !isSampleLookupById && !isSampleLookupByName)
                    {
                        for (ColumnValidator validator : validatorMap.get(pd))
                        {
                            String error = validator.validate(rowNum, o, validatorContext);
                            if (error != null)
                                errors.add(new PropertyValidationError(error, pd.getName()));
                        }
                    }

                    if (participantPD == pd)
                    {
                        participantID = o instanceof String ? (String) o : null;
                    }
                    else if (specimenPD == pd)
                    {
                        specimenID = o instanceof String ? (String) o : null;
                    }
                    else if (visitPD == pd && o != null)
                    {
                        visitID = o instanceof Number ? ((Number) o).doubleValue() : null;
                    }
                    else if (datePD == pd && o != null)
                    {
                        date = o instanceof Date ? (Date) o : null;
                    }
                    else if (targetStudyPD == pd && o != null)
                    {
                        Set<Study> studies = StudyService.get().findStudy(o, null);
                        if (studies.isEmpty())
                        {
                            errors.add(new PropertyValidationError("Couldn't resolve " + pd.getName() + " '" + o + "' to a study folder.", pd.getName()));
                        }
                        else if (studies.size() > 1)
                        {
                            errors.add(new PropertyValidationError("Ambiguous " + pd.getName() + " '" + o + "'.", pd.getName()));
                        }
                        if (!studies.isEmpty())
                        {
                            Study study = studies.iterator().next();
                            targetStudy = study != null ? study.getContainer() : null;
                        }
                    }

                    boolean valueMissing;
                    if (o == null)
                    {
                        valueMissing = true;
                    }
                    else if (o instanceof MvFieldWrapper mvWrapper)
                    {
                        if (mvWrapper.isEmpty())
                            valueMissing = true;
                        else
                        {
                            valueMissing = false;
                            if (!MvUtil.isValidMvIndicator(mvWrapper.getMvIndicator(), dataDomain.getContainer()))
                            {
                                String columnName = pd.getName() + MvColumn.MV_INDICATOR_SUFFIX;
                                wrongTypes.add(columnName);
                                errors.add(new PropertyValidationError(columnName + " must be a valid MV indicator.", columnName));
                            }
                        }

                    }
                    else
                    {
                        valueMissing = false;
                    }

                    // If the column is a file link or attachment, resolve the value to a File object
                    String uri = pd.getType().getTypeURI();
                    if (uri.equals(PropertyType.FILE_LINK.getTypeUri()) || uri.equals(PropertyType.ATTACHMENT.getTypeUri()))
                    {
                        if ("".equals(o))
                        {
                            // Issue 36502: If the original input was an empty value, set it to null so we won't store an empty string in the database
                            o = null;
                            map.put(pd.getName(), null);
                        }
                        else
                        {
                            // File column values are stored as the absolute resolved path
                            try
                            {
                                File resolvedFile = AssayUploadFileResolver.resolve(o, container, pd);
                                if (resolvedFile != null)
                                {
                                    o = resolvedFile;
                                    map.put(pd.getName(), o);
                                }
                            }
                            catch (ValidationException e)
                            {
                                bve.addRowError(e);
                            }
                        }
                    }

                    // If we have a String value for a lookup column, attempt to use the table's unique indices or display value to convert the String into the lookup value
                    // See similar conversion performed in SimpleTranslator.RemapPostConvertColumn
                    // Issue 47509: if the value is a string and is for a SampleId lookup field, let the code below which handles populating materialInputs take care of the remapping.
                    if (o instanceof String s && remappableLookup.containsKey(pd) && !isSampleLookupById)
                    {
                        TableInfo lookupTable = remappableLookup.get(pd);
                        try
                        {
                            Object remapped = cache.remap(lookupTable, s, true);
                            if (remapped == null)
                            {
                                if (pd.getConceptURI() != null && SAMPLE_CONCEPT_URI.equals(pd.getConceptURI()))
                                    errors.add(new PropertyValidationError(o + " not found in the current context.", pd.getName()));
                                else
                                    errors.add(new PropertyValidationError("Failed to convert '" + pd.getName() + "': Could not translate value: " + o, pd.getName()));
                            }
                            else if (o != remapped)
                            {
                                o = remapped;
                                map.put(pd.getName(), remapped);
                            }
                        }
                        catch (ConversionException e)
                        {
                            errors.add(new PropertyValidationError(e.getMessage(), pd.getName()));
                        }
                    }

                    if (!valueMissing && o == ERROR_VALUE && !wrongTypes.contains(pd.getName()))
                    {
                        wrongTypes.add(pd.getName());
                        errors.add(new PropertyValidationError(pd.getName() + " must be of type " + ColumnInfo.getFriendlyTypeName(pd.getPropertyDescriptor().getPropertyType().getJavaType()) + ".", pd.getName()));
                    }

                    // Collect sample names or ids for each of the SampleType lookup columns
                    // Add any sample inputs to the rowInputLSIDs
                    if (o != null && (isSampleLookupById || isSampleLookupByName))
                    {
                        ExpSampleType byNameSS = isSampleLookupByName ? lookupToSampleTypeByName.get(pd) : lookupToSampleTypeById.get(pd);
                        String ssName = byNameSS != null ? byNameSS.getName() : null;
                        Container lookupContainer = pd.getLookup().getContainer() != null ? pd.getLookup().getContainer() : container;

                        // Issue 47509: When samples have names that are numbers, they can be incorrectly interpreted as rowIds during the insert.
                        // If allowLookupByAlternateKey is true or the sample lookup is by name, we call findExpMaterial which will attempt to resolve by name first and then rowId.
                        // If allowLookupByAlternateKey is false, we will only try resolving by the rowId.
                        ExpMaterial material = null;
                        if (settings.isAllowLookupByAlternateKey() || isSampleLookupByName)
                        {
                            String materialName = o.toString();
                            if (inputMaterials.containsKey(materialName))
                                material = inputMaterials.get(materialName);
                            try
                            {
                                if (material == null)
                                    material = exp.findExpMaterial(lookupContainer, user, byNameSS, ssName, materialName, cache, materialCache);
                            }
                            catch (ValidationException ve)
                            {
                                bve.addRowError(ve);
                            }
                        }
                        else if (o instanceof Number n)
                            material = materialCache.computeIfAbsent(n.intValue(), (id) -> exp.getExpMaterial(id, containerFilter));

                        if (material != null)
                        {
                            rowBasedInputMaterials.putIfAbsent(material, pd.getName());
                            rowInputLSIDs.add(material.getLSID());

                            // If the lookup was defined with an explicit container, verify that the sample is in that container
                            boolean matchesLookupContainer = pd.getLookup().getContainer() == null || material.getContainer().equals(pd.getLookup().getContainer());

                            // Issue 47509: Since we have resolved the material here, adjust the data to be imported to the
                            // results table to use the rowIds of the input sample if the lookup is lookupToSampleTypeById.
                            // (note this updates the rawData object passed in to checkData which is used by convertPropertyNamesToURIs to create the fileData object).
                            if (isSampleLookupById && matchesLookupContainer)
                                map.put(pd.getName(), material.getRowId());
                        }
                        // show better error message then the "failed to convert" message that will be hit downstream
                        else if (o instanceof String && isSampleLookupById)
                        {
                            errors.add(new PropertyValidationError(o + " not found in the current context.", pd.getName()));
                        }
                        // check for sample Lookup Validator
                        else if (validatorMap.containsKey(pd))
                        {
                            for (ColumnValidator validator : validatorMap.get(pd))
                            {
                                String error = validator.validate(rowNum, o, validatorContext);
                                if (error != null)
                                    errors.add(new PropertyValidationError(error, pd.getName()));
                            }
                        }
                    }
                }

                if (!errors.isEmpty())
                    bve.addRowError(new ValidationException(errors, rowNum));

                try
                {
                    ParticipantVisit participantVisit = resolver.resolve(specimenID, participantID, visitID, date, targetStudy);
                    if (participantPD != null && map.get(participantPD.getName()) == null)
                    {
                        map.put(participantPD.getName(), participantVisit.getParticipantID());
                    }
                    if (visitPD != null && map.get(visitPD.getName()) == null)
                    {
                        map.put(visitPD.getName(), participantVisit.getVisitID());
                    }
                    if (datePD != null && map.get(datePD.getName()) == null)
                    {
                        map.put(datePD.getName(), participantVisit.getDate());
                    }
                    if (targetStudyPD != null && participantVisit.getStudyContainer() != null)
                    {
                        // Original TargetStudy value may have been a container id, container path, or a study label.
                        // Store all TargetStudy values as Container ID string.
                        map.put(targetStudyPD.getName(), participantVisit.getStudyContainer().getId());
                    }

                    if (resolveMaterials)
                    {
                        ExpMaterial material = participantVisit.getMaterial(false);
                        if (material != null)
                        {
                            rowBasedInputMaterials.putIfAbsent(material, null);
                            rowInputLSIDs.add(material.getLSID());
                        }
                    }
                }
                catch (ExperimentException e)
                {
                    LOG.debug("Failed to resolve participant visit information", e);
                    ValidationException ve = new ValidationException(e.getMessage() == null ? "Failed to resolve participant visit information" : e.getMessage());
                    ve.setRowNumber(rowNum);
                    ve.initCause(e);
                    bve.addRowError(ve);
                }

                // Add any “prov:objectInputs” to the rowInputLSIDs
                var provenanceInputs = map.get(ProvenanceService.PROVENANCE_INPUT_PROPERTY);
                if (null != provenanceInputs)
                {
                    if (provenanceInputs instanceof JSONArray inputJSONArr)
                    {
                        for (Object lsid : inputJSONArr.toList())
                        {
                            rowInputLSIDs.add(lsid.toString());
                        }
                    }
                    else if (provenanceInputs instanceof Collection<?> col)
                    {
                        for (Object obj : col)
                        {
                            rowInputLSIDs.add(Objects.toString(obj));
                        }
                    }
                    else
                    {
                        String lsids = (String) map.get(ProvenanceService.PROVENANCE_INPUT_PROPERTY);
                        String[] lsidArr = lsids.split(",");
                        rowInputLSIDs.addAll(Arrays.asList(lsidArr));
                    }
                }

                if (!rowInputLSIDs.isEmpty())
                {
                    map.put(ProvenanceService.PROVENANCE_INPUT_PROPERTY, rowInputLSIDs);
                }
                return map;
            }
        }).getDataIterator(new DataIteratorContext());
    }


    /** Wraps each map in a version that can be queried based on any of the aliases (name, property URI, import
     * aliases, etc for a given property */
    protected DataIterator convertPropertyNamesToURIs(DataIterator dataMaps, Domain domain)
    {
        // Get the mapping of different names to the set of domain properties
        final Map<String, DomainProperty> importMap = domain.createImportMap(true);

        // For a given property, find all the potential names it by which it could be referenced
        final Map<DomainProperty, Set<String>> propToNames = new HashMap<>();
        for (Map.Entry<String, DomainProperty> entry : importMap.entrySet())
        {
            Set<String> allNames = propToNames.computeIfAbsent(entry.getValue(), k -> new HashSet<>());
            allNames.add(entry.getKey());
        }
        
        // We want to share canonical casing between data rows, or we end up with an extra Map instance for each
        // data row which can add up quickly
        CaseInsensitiveHashMap<Object> caseMapping = new CaseInsensitiveHashMap<>();
        return DataIteratorUtil.mapTransformer(dataMaps, null, dataMap ->
                new PropertyLookupMap(dataMap, caseMapping, importMap, propToNames)
        ).getDataIterator(new DataIteratorContext());
    }

    @Override
    public void deleteData(ExpData data, Container container, User user)
    {
    }

    @Override
    public ActionURL getContentURL(ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(data.getContainer(), protocol, run.getRowId());
        }
        return null;
    }

    /** Wrapper around a row's key->value map that can find the values based on any of the DomainProperty's potential
     * aliases, like the property name, URI, import aliases, etc */
    private static class PropertyLookupMap extends CaseInsensitiveHashMap<Object>
    {
        private final Map<String, DomainProperty> _importMap;
        private final Map<DomainProperty, Set<String>> _propToNames;

        public PropertyLookupMap(Map<String, Object> dataMap, CaseInsensitiveHashMap<Object> caseMapping, Map<String, DomainProperty> importMap, Map<DomainProperty, Set<String>> propToNames)
        {
            super(dataMap, caseMapping);
            _importMap = importMap;
            _propToNames = propToNames;
        }

        @Override
        public Object get(Object key)
        {
            Object result = super.get(key);

            // If we can't find the value based on the name that was passed in, try any of its alternatives
            if (result == null && key instanceof String)
            {
                // Find the property that's associated with that name
                DomainProperty property = _importMap.get(key);
                if (property != null)
                {
                    // Find all the potential synonyms
                    Set<String> allNames = _propToNames.get(property);
                    if (allNames != null)
                    {
                        for (String name : allNames)
                        {
                            // Look for a value under that name
                            result = super.get(name);
                            if (result != null)
                            {
                                break;
                            }
                        }
                    }
                }
            }
            return result;
        }
    }
}
