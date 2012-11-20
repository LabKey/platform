/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.qc.ValidationDataHandler;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Jan 3, 2008
 */
public abstract class AbstractAssayTsvDataHandler extends AbstractExperimentDataHandler implements ValidationDataHandler
{
    protected static final Object ERROR_VALUE = new Object() {
        @Override
        public String toString()
        {
            return "{AbstractAssayTsvDataHandler.ERROR_VALUE}";
        }
    };

    private static final Logger LOG = Logger.getLogger(AbstractAssayTsvDataHandler.class);

    protected abstract boolean allowEmptyData();

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        ExpProtocolApplication sourceApplication = data.getSourceApplication();
        if (sourceApplication == null)
        {
            throw new ExperimentException("Cannot import a TSV without knowing its assay definition");
        }
        ExpRun run = sourceApplication.getRun();
        ExpProtocol protocol = run.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        Map<DataType, List<Map<String, Object>>> rawData = getValidationDataMap(data, dataFile, info, log, context, new DataLoaderSettings());
        assert(rawData.size() <= 1);
        try
        {
            importRows(data, info.getUser(), run, protocol, provider, rawData.values().iterator().next());
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e.toString(), e);
        }
    }

    @Override
    public void beforeDeleteData(List<ExpData> data) throws ExperimentException
    {
        for (ExpData d : data)
        {
            ExpProtocolApplication sourceApplication = d.getSourceApplication();
            if (sourceApplication != null)
            {
                ExpRun run = sourceApplication.getRun();
                ExpProtocol protocol = run.getProtocol();
                AssayProvider provider = AssayService.get().getProvider(protocol);

                Domain domain;
                if (provider != null)
                {
                    domain = provider.getResultsDomain(protocol);
                }
                else
                {
                    // Be tolerant of the AssayProvider no longer being available. See if we have the default
                    // results/data domain for TSV-style assays
                    try
                    {
                        domain = AbstractAssayProvider.getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
                    }
                    catch (IllegalStateException ignored)
                    {
                        domain = null;
                        // Be tolerant of not finding a domain anymore, if the provider has gone away
                    }
                }

                if (domain != null && domain.getStorageTableName() != null)
                {
                    SQLFragment deleteSQL = new SQLFragment("DELETE FROM ");
                    deleteSQL.append(domain.getDomainKind().getStorageSchemaName());
                    deleteSQL.append(".");
                    deleteSQL.append(domain.getStorageTableName());
                    deleteSQL.append(" WHERE DataId = ?");
                    deleteSQL.add(d.getRowId());

                    try
                    {
                        Table.execute(DbSchema.get(domain.getDomainKind().getStorageSchemaName()), deleteSQL);
                    }
                    catch (SQLException e)
                    {
                        throw new ExperimentException(e);
                    }
                }
            }
        }
    }

    public void importRows(ExpData data, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, List<Map<String, Object>> rawData) throws ExperimentException, ValidationException
    {
        try
        {
            ExperimentService.get().ensureTransaction();
            Container container = data.getContainer();
            ParticipantVisitResolver resolver = createResolver(user, run, protocol, provider, container);

            Domain dataDomain = provider.getResultsDomain(protocol);

            if (rawData.size() == 0)
            {
                if (allowEmptyData() || dataDomain.getProperties().length == 0)
                {
                    ExperimentService.get().commitTransaction();
                    return;
                }
                else
                {
                    throw new ExperimentException("Data file contained zero data rows");
                }
            }

            Set<ExpMaterial> inputMaterials = checkData(container, dataDomain, rawData, resolver);

            List<Map<String, Object>> fileData = convertPropertyNamesToURIs(rawData, dataDomain);

            insertRowData(data, user, container, dataDomain, fileData, provider.createProtocolSchema(user, container, protocol, null).createDataTable());

            if (shouldAddInputMaterials())
            {
                AbstractAssayProvider.addInputMaterials(run, user, inputMaterials);
            }

            ExperimentService.get().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }

    protected ParticipantVisitResolver createResolver(User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, Container container)
            throws SQLException, IOException, ExperimentException
    {
        return AssayService.get().createResolver(user, run, protocol, provider, null);
    }

    /** Insert the data into the database.  Transaction is active. */
    protected void insertRowData(ExpData data, User user, Container container, Domain dataDomain, List<Map<String, Object>> fileData, TableInfo tableInfo)
            throws SQLException, ValidationException
    {
        if (tableInfo instanceof UpdateableTableInfo)
        {
            OntologyManager.insertTabDelimited(tableInfo, container, user, new SimpleAssayDataImportHelper(data), fileData, LOG);
        }
        else
        {
            PropertyDescriptor[] dataProperties = new PropertyDescriptor[dataDomain.getProperties().length];
            for (int i = 0; i < dataDomain.getProperties().length; i++)
            {
                dataProperties[i] = dataDomain.getProperties()[i].getPropertyDescriptor();
            }
            Integer id = OntologyManager.ensureObject(container, data.getLSID());
            OntologyManager.insertTabDelimited(container, user, id,
                    new SimpleAssayDataImportHelper(data), dataProperties, fileData, false);
        }
    }

    protected abstract boolean shouldAddInputMaterials();

    private void checkColumns(Domain dataDomain, Set<String> actual, List<String> missing, List<String> unexpected, List<Map<String, Object>> rawData, boolean strict)
    {
        Set<String> checkSet = new CaseInsensitiveHashSet();
        DomainProperty[] expected = dataDomain.getProperties();
        for (DomainProperty pd : expected)
        {
            checkSet.add(pd.getName());
            if (pd.isMvEnabled())
                checkSet.add((pd.getName() + MvColumn.MV_INDICATOR_SUFFIX));
        }
        for (String col : actual)
        {
            if (!checkSet.contains(col))
                unexpected.add(col);
        }
        if (!strict)
        {
            if (unexpected.size() > 0)
                filterColumns(dataDomain, actual, rawData);
            unexpected.clear();
        }

        // Now figure out what's missing but required
        Map<String, DomainProperty> importMap = dataDomain.createImportMap(true);
        // Consider all of them initially
        LinkedHashSet<DomainProperty> missingProps = new LinkedHashSet<DomainProperty>(Arrays.asList(expected));

        // Iterate through the ones we got
        for (String col : actual)
        {
            // Find the property that it maps to (via name, label, import alias, etc)
            DomainProperty prop = importMap.get(col);
            if (prop != null)
            {
                // If there's a match, don't consider it missing any more
                missingProps.remove(prop);
            }
        }

        for (DomainProperty pd : missingProps)
        {
            if ((pd.isRequired() || strict))
                missing.add(pd.getName());
        }
    }

    private void filterColumns(Domain domain, Set<String> actual, List<Map<String, Object>> rawData)
    {
        Map<String,String> expectedKey2ActualKey = new HashMap<String,String>();
        for (Map.Entry<String,DomainProperty> aliased : domain.createImportMap(true).entrySet())
        {
            for (String actualKey : actual)
            {
                if (actualKey.equalsIgnoreCase(aliased.getKey()))
                {
                    expectedKey2ActualKey.put(aliased.getValue().getName(), actualKey);
                }
            }
        }
        ListIterator<Map<String, Object>> iter = rawData.listIterator();
        while (iter.hasNext())
        {
            Map<String, Object> filteredMap = new HashMap<String, Object>();
            Map<String, Object> rawDataRow = iter.next();
            for (Map.Entry<String,String> expectedAndActualKeys : expectedKey2ActualKey.entrySet())
            {
                filteredMap.put(expectedAndActualKeys.getKey(), rawDataRow.get(expectedAndActualKeys.getValue()));
            }
            iter.set(filteredMap);
        }
    }

    /**
     * @return the set of materials that are inputs to this run
     */
    private Set<ExpMaterial> checkData(Container container, Domain dataDomain, List<Map<String, Object>> rawData, ParticipantVisitResolver resolver) throws IOException, ValidationException
    {
        List<String> missing = new ArrayList<String>();
        List<String> unexpected = new ArrayList<String>();

        Set<String> columnNames = rawData.get(0).keySet();
        // For now, we'll only enforce that required columns are present.  In the future, we'd like to
        // do a strict check first, and then present ignorable warnings.
        checkColumns(dataDomain, columnNames, missing, unexpected, rawData, false);
        if (!missing.isEmpty() || !unexpected.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            if (!missing.isEmpty())
            {
                builder.append("Expected columns were not found: ");
                for (java.util.Iterator<String> it = missing.iterator(); it.hasNext();)
                {
                    builder.append(it.next());
                    if (it.hasNext())
                        builder.append(", ");
                    else
                        builder.append(".  ");
                }
            }
            if (!unexpected.isEmpty())
            {
                builder.append("Unexpected columns were found: ");
                for (java.util.Iterator<String> it = unexpected.iterator(); it.hasNext();)
                {
                    builder.append(it.next());
                    if (it.hasNext())
                        builder.append(", ");
                }
            }
            throw new ValidationException(builder.toString());
        }

        DomainProperty participantPD = null;
        DomainProperty specimenPD = null;
        DomainProperty visitPD = null;
        DomainProperty datePD = null;
        DomainProperty targetStudyPD = null;

        DomainProperty[] columns = dataDomain.getProperties();

        for (DomainProperty pd : columns)
        {
            if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                participantPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                specimenPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.VISITID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.DOUBLE)
            {
                visitPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.DATE_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.DATE_TIME)
            {
                datePD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                targetStudyPD = pd;
            }
        }

        boolean resolveMaterials = specimenPD != null || visitPD != null || datePD != null || targetStudyPD != null;

        Set<String> missingValues = new HashSet<String>();
        Set<String> wrongTypes = new HashSet<String>();

        StringBuilder errorSB = new StringBuilder();

        Set<ExpMaterial> materialInputs = new LinkedHashSet<ExpMaterial>();

        Map<String, DomainProperty> aliasMap = dataDomain.createImportMap(true);

        // We want to share canonical casing between data rows, or we end up with an extra Map instance for each
        // data row which can add up quickly
        CaseInsensitiveHashMap<Object> caseMapping = new CaseInsensitiveHashMap<Object>();

        for (ListIterator<Map<String, Object>> iter = rawData.listIterator(); iter.hasNext();)
        {
            Map<String, Object> originalMap = iter.next();
            Map<String, Object> map = new CaseInsensitiveHashMap<Object>(caseMapping);
            // Rekey the map, resolving aliases to the actual property names
            for (Map.Entry<String, Object> entry : originalMap.entrySet())
            {
                DomainProperty prop = aliasMap.get(entry.getKey());
                if (prop != null)
                {
                    map.put(prop.getName(), entry.getValue());
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
                if (participantPD == pd)
                {
                    participantID = o instanceof String ? (String)o : null;
                }
                else if (specimenPD == pd)
                {
                    specimenID = o instanceof String ? (String)o : null;
                }
                else if (visitPD == pd && o != null)
                {
                    visitID = o instanceof Number ? ((Number)o).doubleValue() : null;
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
                        errorSB.append("Couldn't resolve ").append(pd.getName()).append(" '").append(o.toString()).append("' to a study folder.");
                    }
                    else if (studies.size() > 1)
                    {
                        errorSB.append("Ambiguous ").append(pd.getName()).append(" '").append(o.toString()).append("'.");
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
                else if (o instanceof MvFieldWrapper)
                {
                    MvFieldWrapper mvWrapper = (MvFieldWrapper)o;
                    if (mvWrapper.isEmpty())
                        valueMissing = true;
                    else
                    {
                        valueMissing = false;
                        if (!MvUtil.isValidMvIndicator(mvWrapper.getMvIndicator(), dataDomain.getContainer()))
                        {
                            String columnName = pd.getName() + MvColumn.MV_INDICATOR_SUFFIX;
                            wrongTypes.add(columnName);
                            errorSB.append(columnName).append(" must be a valid MV indicator.");
                        }
                    }

                }
                else
                {
                    valueMissing = false;
                }

                // UNDONE: Move this check into OntologyManager.insertTabDelimeted
                // For security reasons, make sure the user hasn't tried to reference a file that's not under
                // the pipeline root. Otherwise, they could get access to any file on the server
                if (o instanceof File)
                {
                    PipeRoot root = PipelineService.get().findPipelineRoot(container);
                    if (root == null || !root.isUnderRoot((File)o))
                    {
                        throw new ValidationException("Cannot reference file " + o + " from container " + container);
                    }
                }

                if (pd.isRequired() && valueMissing && !missingValues.contains(pd.getName()))
                {
                    missingValues.add(pd.getName());
                    errorSB.append(pd.getName()).append(" is required. ");
                }
                else if (!valueMissing && o == ERROR_VALUE && !wrongTypes.contains(pd.getName()))
                {
                    wrongTypes.add(pd.getName());
                    errorSB.append(pd.getName()).append(" must be of type ");
                    errorSB.append(ColumnInfo.getFriendlyTypeName(pd.getPropertyDescriptor().getPropertyType().getJavaType())).append(". ");
                }
            }

            ParticipantVisit participantVisit = resolver.resolve(specimenID, participantID, visitID, date, targetStudy);
            if (participantPD != null && map.get(participantPD.getName()) == null)
            {
                map.put(participantPD.getName(), participantVisit.getParticipantID());
                iter.set(map);
            }
            if (visitPD != null && map.get(visitPD.getName()) == null)
            {
                map.put(visitPD.getName(), participantVisit.getVisitID());
                iter.set(map);
            }
            if (datePD != null && map.get(datePD.getName()) == null)
            {
                map.put(datePD.getName(), participantVisit.getDate());
                iter.set(map);
            }
            if (targetStudyPD != null && participantVisit.getStudyContainer() != null)
            {
                // Original TargetStudy value may have been a container id, container path, or a study label.
                // Store all TargetStudy values as Container ID string.
                map.put(targetStudyPD.getName(), participantVisit.getStudyContainer().getId());
                iter.set(map);
            }

            if (resolveMaterials)
            {
                materialInputs.add(participantVisit.getMaterial());
            }
        }

        if (errorSB.length() != 0)
        {
            throw new ValidationException("There are errors in the uploaded data: " + errorSB.toString());
        }

        return materialInputs;
    }

    /** Wraps each map in a version that can be queried based on on any of the aliases (name, property URI, import
     * aliases, etc for a given property */
    protected List<Map<String, Object>> convertPropertyNamesToURIs(List<Map<String, Object>> dataMaps, Domain domain)
    {
        // Get the mapping of different names to the set of domain properties
        final Map<String, DomainProperty> importMap = domain.createImportMap(true);

        // For a given property, find all the potential names it by which it could be referenced
        final Map<DomainProperty, Set<String>> propToNames = new HashMap<DomainProperty, Set<String>>();
        for (Map.Entry<String, DomainProperty> entry : importMap.entrySet())
        {
            Set<String> allNames = propToNames.get(entry.getValue());
            if (allNames == null)
            {
                allNames = new HashSet<String>();
                propToNames.put(entry.getValue(), allNames);
            }
            allNames.add(entry.getKey());
        }
        
        // We want to share canonical casing between data rows, or we end up with an extra Map instance for each
        // data row which can add up quickly
        CaseInsensitiveHashMap<Object> caseMapping = new CaseInsensitiveHashMap<Object>();
        for (ListIterator<Map<String, Object>> i = dataMaps.listIterator(); i.hasNext(); )
        {
            Map<String, Object> dataMap = i.next();
            CaseInsensitiveHashMap<Object> newMap = new PropertyLookupMap(dataMap, caseMapping, importMap, propToNames);

            // Swap out the entry in the list with the transformed map
            i.set(newMap);
        }
        return dataMaps;
    }

    public void deleteData(ExpData data, Container container, User user)
    {
        OntologyManager.deleteOntologyObjects(container, data.getLSID());
    }

    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }

    public ActionURL getContentURL(Container container, ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(container, protocol, run.getRowId());
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
                    // Find all of the potential synonyms
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
