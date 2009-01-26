/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.common.util.Pair;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.*;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 16, 2006
 * Time: 1:11:27 PM
 */
public class AssayPublishManager implements AssayPublishService.Service
{
    private TableInfo tinfoUpdateLog;
    private static final int MIN_ASSAY_ID = 5000;
    public static final String ASSAY_PUBLISH_AUDIT_EVENT = "AssayPublishAuditEvent";

    public synchronized static AssayPublishManager getInstance()
    {
        return (AssayPublishManager) AssayPublishService.get();
    }


    private TableInfo getTinfoUpdateLog()
    {
        if (tinfoUpdateLog == null)
            tinfoUpdateLog = StudySchema.getInstance().getTableInfoUploadLog();
        return tinfoUpdateLog;
    }
    
    public Map<Container, String> getValidPublishTargets(User user, int permission)
    {
        Set<Container> writableContainers = ContainerManager.getContainerSet(ContainerManager.getContainerTree(), user, permission);
        Map<Container, String> studyContainers = new TreeMap<Container, String>(new Comparator<Container>()
        {
            public int compare(Container c1, Container c2)
            {
                return c1.getPath().compareToIgnoreCase(c2.getPath());
            }
        });

        for (Container container : writableContainers)
        {
            Study study = StudyManager.getInstance().getStudy(container);
            if (study != null && !"/".equals(container.getPath()))
                studyContainers.put(container, study.getLabel());
        }
        return studyContainers;
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                          Map<String, Object>[] dataMaps, Map<String, PropertyType> types, String keyPropertyName, List<String> errors)
            throws SQLException, IOException, ServletException
    {
        List<PropertyDescriptor> propertyDescriptors = new ArrayList<PropertyDescriptor>();
        for (Map.Entry<String, PropertyType> entry : types.entrySet())
        {
            String pdName = entry.getKey();
            PropertyType type = types.get(pdName);
            String typeURI = type.getTypeUri();
            PropertyDescriptor pd = new PropertyDescriptor(null,
                    typeURI, pdName, targetContainer);
            if (type.getJavaType() == Double.class)
                pd.setFormat("0.###");
            propertyDescriptors.add(pd);
        }
        return publishAssayData(user, sourceContainer, targetContainer, assayName, protocol, dataMaps, propertyDescriptors, keyPropertyName, errors);
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                         Map<String, Object>[] dataMaps, Map<String, PropertyType> types, List<String> errors)
            throws SQLException, IOException, ServletException
    {
        return publishAssayData(user, sourceContainer, targetContainer, assayName, protocol, dataMaps, types, null, errors);
    }

    private List<PropertyDescriptor> createTargetPropertyDescriptors(DataSetDefinition dataset, List<PropertyDescriptor> sourcePds, List<String> errors)
    {
        List<PropertyDescriptor> targetPds = new ArrayList<PropertyDescriptor>(sourcePds.size());
        Set<String> legalNames = new HashSet<String>();
        for (PropertyDescriptor sourcePd : sourcePds)
        {
            PropertyDescriptor targetPd = sourcePd.clone();

            // Deal with duplicate legal names.  It's too bad that we have to do so this late in the game
            // (rather than at assay design time), but for a long time there was no mechanism to
            // prevent assay designers from creating properties with names that are the same as hard columns.
            // There are also a few cases where an assay provider may add columns to the published set as
            // publish time; rather than reserve all these names at design time, we catch them here.
            String legalName = ColumnInfo.legalNameFromName(targetPd.getName()).toLowerCase();
            if (legalNames.contains(legalName))
            {
                errors.add("Unable to copy to study: duplicate column \"" + targetPd.getName() + "\" detected in the assay design.  Please contact an administrator.");
                return Collections.emptyList();
            }
            legalNames.add(legalName);

            targetPd.setPropertyURI(dataset.getTypeURI() + "#" + sourcePd.getName());
            targetPd.setContainer(dataset.getContainer());
            targetPd.setProject(dataset.getContainer().getProject());
            if (targetPd.getLookupQuery() != null)
                targetPd.setLookupContainer(sourcePd.getLookupContainer());
            // set the ID to zero so it's clear that this is a new property descriptor:
            targetPd.setPropertyId(0);
            targetPds.add(targetPd);
        }
        return targetPds;
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, @Nullable ExpProtocol protocol,
                                          Map<String, Object>[] dataMaps, List<PropertyDescriptor> columns, String keyPropertyName, List<String> errors)
            throws SQLException, IOException, UnauthorizedException
    {
        Study targetStudy = StudyManager.getInstance().getStudy(targetContainer);
        assert verifyRequiredColumns(dataMaps, targetStudy.isDateBased());
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean ownsTransaction = !scope.isTransactionActive();

        try
        {
            if (ownsTransaction)
                scope.beginTransaction();
            DataSetDefinition[] datasets = StudyManager.getInstance().getDataSetDefinitions(targetStudy);
            String typeURI = getDomainURIString(targetStudy, assayName);
            DataSetDefinition dataset = null;
            for (int i = 0; i < datasets.length && dataset == null; i++)
            {
                if (datasets[i].getTypeURI() != null && datasets[i].getTypeURI().equals(typeURI))
                    dataset = datasets[i];
            }
            if (dataset == null)
                dataset = createAssayDataset(user, targetStudy, assayName, keyPropertyName, null, false, protocol);
            else if (protocol != null)
            {
                Integer datasetProtocolId = dataset.getProtocolId();
                if (datasetProtocolId == null)
                {
                    dataset.setProtocolId(protocol.getRowId());
                    StudyManager.getInstance().updateDataSetDefinition(user, dataset);
                }
                else if (!datasetProtocolId.equals(protocol.getRowId()))
                {
                    errors.add("The destination dataset belongs to a different assay protocol");
                    return null;
                }
            }

            List<PropertyDescriptor> types = createTargetPropertyDescriptors(dataset, columns, errors);
            if (!errors.isEmpty())
                return null;
            Map<String, String> propertyNamesToUris = ensurePropertyDescriptors(targetContainer, user, dataset, dataMaps, types);
            Map<String, Object>[] convertedDataMaps = convertPropertyNamesToURIs(dataMaps, propertyNamesToUris);
            // re-retrieve the datasetdefinition: this is required to pick up any new columns that may have been created
            // in 'ensurePropertyDescriptors'.
            dataset = StudyManager.getInstance().getDataSetDefinition(targetStudy, dataset.getRowId());
            if (ownsTransaction)
                scope.commitTransaction();
            Integer defaultQCStateId = targetStudy.getDefaultAssayQCState();
            QCState defaultQCState = null;
            if (defaultQCStateId != null)
                defaultQCState = StudyManager.getInstance().getQCStateForRowId(targetContainer, defaultQCStateId.intValue());
            // unfortunately, the actual import cannot happen within our transaction: we eventually hit the
            // IllegalStateException in ContainerManager.ensureContainer.
            String[] lsids = StudyManager.getInstance().importDatasetData(targetStudy, user, dataset, convertedDataMaps, new Date().getTime(), errors, true, defaultQCState);
            if (lsids.length > 0 && protocol != null)
            {
                for (Map.Entry<String, int[]> entry : getSourceLSID(dataMaps).entrySet())
                {
                    AuditLogEvent event = new AuditLogEvent();

                    event.setCreatedBy(user);
                    event.setEventType(ASSAY_PUBLISH_AUDIT_EVENT);
                    event.setIntKey1(protocol.getRowId());
                    event.setComment(entry.getValue()[0] + " row(s) were copied to a study from the assay: " + protocol.getName());
                    event.setKey1(targetContainer.getId());
                    event.setContainerId(sourceContainer.getId());

                    Map<String, Object> dataMap = new HashMap<String, Object>();
                    dataMap.put("datasetId", dataset.getDataSetId());

                    dataMap.put("sourceLsid", entry.getKey());
                    dataMap.put("recordCount", entry.getValue()[0]);

                    AssayAuditViewFactory.getInstance().ensureDomain(user);
                    AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(ASSAY_PUBLISH_AUDIT_EVENT));
                }
            }
            //Make sure that the study is updated with the correct timepoints.
            StudyManager.getInstance().getVisitManager(targetStudy).updateParticipantVisits(user);
            StudyManager.getInstance().updateParticipantCohorts(user, targetStudy);

            ActionURL url = new ActionURL(StudyController.DatasetAction.class, targetContainer);
            url.addParameter(DataSetDefinition.DATASETKEY, dataset.getRowId());
            if (StudyManager.getInstance().showQCStates(targetStudy.getContainer()))
            {
                QCStateSet allStates = QCStateSet.getAllStates(targetStudy.getContainer());
                if (allStates != null)
                    url.addParameter(BaseStudyController.SharedFormParameters.QCState, allStates.getFormValue());
            }

            return url;
        }
        finally
        {
            if (ownsTransaction)
                scope.closeConnection();
        }
    }

    private Map<String, int[]> getSourceLSID(Map<String, Object>[] dataMaps)
    {
        Map<String, int[]> lsidMap = new HashMap<String, int[]>();

        for (Map<String, Object> map : dataMaps)
        {
            for (Map.Entry<String, Object> entry : map.entrySet())
            {
                if (entry.getKey().equalsIgnoreCase("sourcelsid"))
                {
                    String lsid = String.valueOf(entry.getValue());
                    int[] count = lsidMap.get(lsid);
                    if (count == null)
                    {
                        count = new int[1];
                        lsidMap.put(lsid, count);
                    }
                    count[0]++;
                    break;
                }
            }
        }
        return lsidMap;
    }

    private boolean verifyRequiredColumns(Map<String, Object>[] dataMaps, boolean isDateBased)
    {
        for (Map<String, Object> dataMap : dataMaps)
        {
            Set<String> lcaseSet = new HashSet<String>();
            for (String key : dataMap.keySet())
                lcaseSet.add(key.toLowerCase());
            assert lcaseSet.contains("participantid") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
            assert isDateBased || lcaseSet.contains("sequencenum") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
            assert !isDateBased || lcaseSet.contains("date") : "Publishable assay results must include participantid, date, and sourcelsid columns.";
            //assert lcaseSet.contains("sourcelsid") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
        }
        return true;
    }

    private Map<String, Object>[] convertPropertyNamesToURIs(Map<String, Object>[] dataMaps, Map<String, String> propertyNamesToUris)
    {
        Map<String, Object>[] ret = new Map[dataMaps.length];
        for (int i = 0; i < dataMaps.length; i++)
        {
            ret[i] = new CaseInsensitiveHashMap<Object>(dataMaps[i].size());
            for (Map.Entry<String,Object> entry : dataMaps[i].entrySet())
            {
                String uri = propertyNamesToUris.get(entry.getKey());
                assert uri != null : "Expected all properties to already be present in assay type";
                ret[i].put(uri, entry.getValue());
            }
        }
        return ret;
    }

    private Map<String, String> ensurePropertyDescriptors(Container container, User user, DataSetDefinition dataset,
                                                          Map<String, Object>[] dataMaps, List<PropertyDescriptor> types) throws SQLException, UnauthorizedException
    {
        PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(dataset.getTypeURI(), container);
        // we'll return a mapping from column name to column uri
        Map<String, String> propertyNamesToUris = new CaseInsensitiveHashMap<String>();

        // add ontology properties to our return map
        for (PropertyDescriptor pd : pds)
            propertyNamesToUris.put(pd.getName(), pd.getPropertyURI());

        // add hard columns to our return map
        for (ColumnInfo col : dataset.getTableInfo(user).getColumns())
            propertyNamesToUris.put(col.getName(), col.getPropertyURI());

        // create a set of all columns that will be required, so we can detect
        // if any of these are new
        Set<String> newPdNames = new TreeSet<String>();
        for (Map<String, Object> dataMap : dataMaps)
            newPdNames.addAll(dataMap.keySet());

        Map<String, PropertyDescriptor> typeMap = new HashMap<String, PropertyDescriptor>();
        for (PropertyDescriptor pd : types)
            typeMap.put(pd.getName(), pd);
        // loop through all new columns, and verify that we have a property already defined:
        DomainDescriptor domainDescriptor = new DomainDescriptor(dataset.getTypeURI(), container);
        for (String newPdName : newPdNames)
        {
            if (!propertyNamesToUris.containsKey(newPdName))
            {
                PropertyDescriptor pd = typeMap.get(newPdName);
                OntologyManager.insertOrUpdatePropertyDescriptor(pd, domainDescriptor);
                propertyNamesToUris.put(newPdName, pd.getPropertyURI());
            }
        }
        return propertyNamesToUris;
    }
    
    public DataSetDefinition createAssayDataset(User user, Study study, String name, String keyPropertyName, Integer datasetId, boolean isDemographicData, ExpProtocol protocol) throws SQLException
    {
        boolean ownTransaction = false;
        DbSchema schema = StudySchema.getInstance().getSchema();
        try
        {
            if (!schema.getScope().isTransactionActive())
            {
                schema.getScope().beginTransaction();
                ownTransaction = true;
            }

            if (null == datasetId)
                datasetId = Table.executeSingleton(schema, "SELECT MAX(n) + 1 AS id FROM (SELECT Max(datasetid) AS n FROM study.dataset WHERE container=? UNION SELECT ? As n) x", new Object[] {study.getContainer().getId(), MIN_ASSAY_ID}, Integer.class);
            DataSetDefinition newDataSet = new DataSetDefinition(study, datasetId, name, null, getDomainURIString(study, name));
            newDataSet.setShowByDefault(true);
            if (keyPropertyName != null)
                newDataSet.setKeyPropertyName(keyPropertyName);
            newDataSet.setDemographicData(isDemographicData);
            if (protocol != null)
                newDataSet.setProtocolId(protocol.getRowId());

            StudyManager.getInstance().createDataSetDefinition(user, newDataSet);
            if (ownTransaction)
            {
                schema.getScope().commitTransaction();
                ownTransaction = false;
            }
            return newDataSet;
        }
        finally
        {
            if (ownTransaction)
                schema.getScope().rollbackTransaction();
        }
    }

    static final String DIR_NAME = "assaydata";
    public UploadLog saveUploadData(User user, DataSetDefinition dsd, String tsv) throws IOException
    {
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(dsd.getContainer());
        if (null == pipelineRoot)
            throw new IOException("Please have your administrator set up a pipeline root for this folder.");
        File rootFile = pipelineRoot.getRootPath();

        if (!NetworkDrive.exists(rootFile))
            throw new IOException("Pipeline directory: " + rootFile + " does not exist. Please see your administrator.");

        File dir = new File(rootFile, DIR_NAME);
        if (!dir.exists())
        {
            boolean success = dir.mkdir();
            if (!success)
                throw new IOException("Could not create directory: " + dir);
        }

        //File name is studyname_datasetname_date_hhmm.ss
        Date dateCreated = new Date();
        String dateString = DateUtil.formatDateTime(dateCreated, "yyy-MM-dd-HHmm");
        int id = 0;
        File file;
        do
        {
            String extra = id++ == 0 ? "" : String.valueOf(id);
            String fileName = dsd.getStudy().getLabel() + "-" + dsd.getLabel() + "-" + dateString + extra + ".tsv";
            fileName = fileName.replace('\\', '_').replace('/','_').replace(':','_');
            file = new File(dir, fileName);
        }
        while (file.exists());
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(file);
            writer.append(tsv);
            writer.close();
            writer = null;
        }
        finally
        {
            if (null != writer)
                try { writer.close(); } catch (Exception x) {}
        }

        UploadLog ul = new UploadLog();
        ul.setContainer(dsd.getContainer());
        ul.setDatasetId(dsd.getDataSetId());
        ul.setCreated(dateCreated);
        ul.setUserId(user.getUserId());
        ul.setStatus("Initializing");
        ul.setFilePath(file.getPath());

        try
        {
            ul = Table.insert(user, getTinfoUpdateLog(), ul);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return ul;
    }

    public String getDomainURIString(Study study, String typeName)
    {
        Lsid domainLsid = new Lsid("StudyDataset", "Folder-" + study.getContainer().getRowId(), typeName);
        return domainLsid.toString();
    }

    /**
     * Return an array of LSIDs from the newly created dataset entries,
     * along with the upload log.
     */
    public Pair<String[],UploadLog> importDatasetTSV(User user, Study study, DataSetDefinition dsd, String tsv, Map<String, String> columnMap, List<String> errors) throws SQLException, ServletException
    {
        UploadLog ul = null;
        String[] lsids = new String[0];
        try
        {
            ul = saveUploadData(user, dsd, tsv);
            Integer defaultQCStateId = study.getDefaultDirectEntryQCState();
            QCState defaultQCState = null;
            if (defaultQCStateId != null)
                defaultQCState = StudyManager.getInstance().getQCStateForRowId(study.getContainer(), defaultQCStateId.intValue());
            lsids = StudyManager.getInstance().importDatasetTSV(study, user, dsd, tsv, ul.getCreated().getTime(), columnMap, errors, true, defaultQCState);
            if (errors.size() == 0)
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user);
        }
        catch (IOException x)
        {
            errors.add("Exception: " + x.getMessage());
            if (ul != null)
            {
                ul.setStatus("ERROR");
                String description = ul.getDescription();
                ul.setDescription(description == null ? "" : description + "\n" + new Date() + ":" + x.getMessage());
                try
                {
                    ul = Table.update(user, StudySchema.getInstance().getTableInfoUploadLog(), ul, ul.getRowId(), null);
                    return Pair.of(lsids, ul);
                }
                catch (SQLException s)
                {
                    //throw original
                }
            }
        }

        if (errors.size() == 0)
        {
            //Update the status
            assert ul != null : "Upload log should always exist if no errors have occurred.";
            ul.setStatus("SUCCESS");
            ul = Table.update(user, getTinfoUpdateLog(), ul, ul.getRowId(), null);
        }
        else if (ul != null)
        {
            ul.setStatus("ERROR");
            StringBuffer sb = new StringBuffer();
            String sep = "";
            for (String s : errors)
            {
                sb.append(sep).append(s);
                sep = "\n";
            }
            ul.setDescription(sb.toString());
            ul = Table.update(user, getTinfoUpdateLog(), ul, ul.getRowId(), null);
        }
        return Pair.of(lsids,ul);
    }

    public UploadLog getUploadLog(Container c, int id) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("container", c.getId());
        filter.addCondition("rowId", id);

        return Table.selectObject(getTinfoUpdateLog(), filter, null, UploadLog.class);
    }
    
    public ActionURL getPublishHistory(Container c, ExpProtocol protocol)
    {
        return getPublishHistory(c, protocol, null);
    }

    public ActionURL getPublishHistory(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
    {
        if (protocol != null)
        {
            ActionURL url = new ActionURL("assay", "publishHistory", container).addParameter("rowId", protocol.getRowId());
            if (containerFilter != null)
                url.addParameter("containerFilterName", containerFilter.name());
            return url;
        }

        HttpView.throwNotFound("Specified protocol is invalid");
        return null;
    }

    public TimepointType getTimepointType(Container container)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new IllegalArgumentException("No study in container: " + container.getPath());

        return study.isDateBased() ? TimepointType.DATE : TimepointType.VISIT;
    }
}
