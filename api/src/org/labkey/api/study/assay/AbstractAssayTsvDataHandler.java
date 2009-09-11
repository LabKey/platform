/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.ValidationDataHandler;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

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
    protected static final Object ERROR_VALUE = new Object();

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

        Map<DataType, List<Map<String, Object>>> rawData = getValidationDataMap(data, dataFile, info, log, context);
        assert(rawData.size() <= 1);
        importRows(data, info.getUser(), run, protocol, provider, rawData.values().iterator().next());
    }

    public void importRows(ExpData data, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, List<Map<String, Object>> rawData) throws ExperimentException
    {
        boolean transaction = false;
        try
        {
            Container container = data.getContainer();
            Integer id = OntologyManager.ensureObject(container, data.getLSID());

            List<DomainProperty> allProps = new ArrayList<DomainProperty>();
            allProps.addAll(Arrays.asList(provider.getBatchDomain(protocol).getProperties()));
            allProps.addAll(Arrays.asList(provider.getRunDomain(protocol).getProperties()));

            Map<String, Object> props = OntologyManager.getProperties(container, run.getLSID());
            ExpExperiment batch = AssayService.get().findBatch(run);
            if (batch != null)
            {
                Map<String, Object> batchProps = OntologyManager.getProperties(container, batch.getLSID());
                props.putAll(batchProps);
            }
            ParticipantVisitResolver resolver = null;

            Container targetContainer = null;
            for (DomainProperty runProp : allProps)
            {
                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(runProp.getName()))
                {
                    Object targetObject = props.get(runProp.getPropertyURI());
                    if (targetObject instanceof String)
                    {
                        targetContainer = ContainerManager.getForId((String)targetObject);
                        break;
                    }
                }
            }

            for (DomainProperty runProp : allProps)
            {
                if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equalsIgnoreCase(runProp.getName()))
                {
                    Object targetObject = props.get(runProp.getPropertyURI());
                    if (targetObject instanceof String)
                    {
                        ParticipantVisitResolverType resolverType = AbstractAssayProvider.findType((String)targetObject, provider.getParticipantVisitResolverTypes());
                        resolver = resolverType.createResolver(ExperimentService.get().getExpRun(run.getRowId()), targetContainer, user);
                        break;
                    }
                }
            }

            if (resolver == null)
            {
                resolver = new StudyParticipantVisitResolver(container, targetContainer);
            }

            if (rawData.size() == 0)
            {
                if (allowEmptyData())
                {
                    return;
                }
                else
                {
                    throw new ExperimentException("Data file contained zero data rows");
                }
            }

            Domain dataDomain = provider.getResultsDomain(protocol);

            Set<ExpMaterial> inputMaterials = checkData(dataDomain, rawData, resolver);
            DomainProperty[] dataDPs = dataDomain.getProperties();
            PropertyDescriptor[] dataProperties = new PropertyDescriptor[dataDPs.length];
            for (int i = 0; i < dataDPs.length; i++)
                dataProperties[i] = dataDPs[i].getPropertyDescriptor();
            Map<String, DomainProperty> propertyNameToDescriptor = dataDomain.createImportMap(true);
            List<Map<String, Object>> fileData = convertPropertyNamesToURIs(rawData, propertyNameToDescriptor);

            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                transaction = true;
            }

            OntologyManager.insertTabDelimited(container, id,
                    new SimpleAssayDataImportHelper(data.getLSID()), dataProperties, fileData, false);

            if (inputMaterials.isEmpty())
            {
                throw new ExperimentException("Could not find any input samples in the data");
            }

            if (shouldAddInputMaterials())
            {
                AbstractAssayProvider.addInputMaterials(run, user, inputMaterials);
            }

            if (transaction)
            {
                ExperimentService.get().commitTransaction();
                transaction = false;
            }
        }
        catch (ValidationException ve)
        {
            throw new ExperimentException(ve.toString(), ve);
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        finally
        {
            if (transaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    protected abstract boolean shouldAddInputMaterials();

    private void checkColumns(Domain dataDomain, Set<String> actual, List<String> missing, List<String> unexpected, List<Map<String, Object>> rawData, boolean strict)
    {
        Set<String> checkSet = new HashSet<String>();
        DomainProperty[] expected = dataDomain.getProperties();
        for (DomainProperty pd : expected)
        {
            checkSet.add(pd.getName().toLowerCase());
            if (pd.isMvEnabled())
                checkSet.add((pd.getName() + MvColumn.MV_INDICATOR_SUFFIX).toLowerCase());
        }
        for (String col : actual)
        {
            if (!checkSet.contains(col.toLowerCase()))
                unexpected.add(col);
        }
        checkSet.clear();
        if (!strict)
        {
            if (unexpected.size() > 0)
                filterColumns(dataDomain, actual, rawData);
            unexpected.clear();
        }

        for (String col : actual)
            checkSet.add(col.toLowerCase());

        for (DomainProperty pd : expected)
        {
            if ((pd.isRequired() || strict) && !checkSet.contains(pd.getName().toLowerCase()))
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
        for (int i = 0; i < rawData.size(); i++)
        {
            Map<String, Object> filteredMap = new HashMap<String, Object>();
            for (Map.Entry<String,String> expectedAndActualKeys : expectedKey2ActualKey.entrySet())
            {
                filteredMap.put(expectedAndActualKeys.getKey(), rawData.get(i).get(expectedAndActualKeys.getValue()));
            }
            rawData.set(i, filteredMap);
        }
    }

    /**
     * @return the set of materials that are inputs to this run
     */
    private Set<ExpMaterial> checkData(Domain dataDomain, List<Map<String, Object>> rawData, ParticipantVisitResolver resolver) throws IOException, ExperimentException
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
            throw new ExperimentException(builder.toString());
        }

        DomainProperty participantPD = null;
        DomainProperty specimenPD = null;
        DomainProperty visitPD = null;
        DomainProperty datePD = null;

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
        }

        Set<String> missingValues = new HashSet<String>();
        Set<String> wrongTypes = new HashSet<String>();

        StringBuilder errorSB = new StringBuilder();

        Set<ExpMaterial> materialInputs = new LinkedHashSet<ExpMaterial>();

        Map<String, DomainProperty> aliasMap = dataDomain.createImportMap(true);

        for (int i = 0; i < rawData.size(); i++)
        {
            Map<String, Object> map = new CaseInsensitiveHashMap<Object>();
            // Rekey the map, resolving aliases to the actual property names
            for (Map.Entry<String, Object> entry : rawData.get(i).entrySet())
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
                else if (datePD == pd & o != null)
                {
                    date = o instanceof Date ? (Date) o : null;
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
            ParticipantVisit participantVisit = resolver.resolve(specimenID, participantID, visitID, date);
            if (participantPD != null && map.get(participantPD.getName()) == null)
            {
                map.put(participantPD.getName(), participantVisit.getParticipantID());
                rawData.set(i, map);
            }
            if (visitPD != null && map.get(visitPD.getName()) == null)
            {
                map.put(visitPD.getName(), participantVisit.getVisitID());
                rawData.set(i, map);
            }
            if (datePD != null && map.get(datePD.getName()) == null)
            {
                map.put(datePD.getName(), participantVisit.getDate());
                rawData.set(i, map);
            }

            materialInputs.add(participantVisit.getMaterial());
        }

        if (errorSB.length() != 0)
        {
            throw new ExperimentException("There are errors in the uploaded data: " + errorSB.toString());
        }

        return materialInputs;
    }

    private List<Map<String, Object>> convertPropertyNamesToURIs(List<Map<String, Object>> dataMaps, Map<String, DomainProperty> propertyNamesToUris)
    {
        List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>(dataMaps.size());
        for (Map<String, Object> dataMap : dataMaps)
        {
            CaseInsensitiveHashMap<Object> newMap = new CaseInsensitiveHashMap<Object>(dataMap.size());
            for (Map.Entry<String, Object> entry : dataMap.entrySet())
            {
                DomainProperty pd = propertyNamesToUris.get(entry.getKey().toLowerCase());
                if (pd == null)
                    throw new RuntimeException("Expected uri for datamap property '" + entry.getKey() + "'.");
                newMap.put(pd.getPropertyURI(), entry.getValue());
            }
            ret.add(newMap);
        }
        return ret;
    }

    public void deleteData(ExpData data, Container container, User user)
    {
        try
        {
            OntologyManager.deleteOntologyObjects(container, data.getLSID());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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
}
