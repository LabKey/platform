/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;
import org.labkey.api.data.*;
import org.labkey.api.study.TimepointType;

import javax.servlet.ServletException;
import java.util.*;
import java.sql.SQLException;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jan 26, 2009
 */
public abstract class AbstractTsvAssayProvider extends AbstractAssayProvider
{
    public AbstractTsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, DataType dataType)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType);
    }

    public ExpData getDataForDataRow(Object dataRowId)
    {
        if (dataRowId == null)
            return null;

        Integer id;
        if (dataRowId instanceof Integer)
        {
            id = (Integer)dataRowId;
        }
        else
        {
            try
            {
                id = Integer.parseInt(dataRowId.toString());
            }
            catch (NumberFormatException nfe)
            {
                return null;
            }
        }

        OntologyObject dataRow = OntologyManager.getOntologyObject(id.intValue());
        if (dataRow == null)
            return null;
        OntologyObject dataRowParent = OntologyManager.getOntologyObject(dataRow.getOwnerObjectId().intValue());
        if (dataRowParent == null)
            return null;
        return ExperimentService.get().getExpData(dataRowParent.getObjectURI());
    }

    public ActionURL copyToStudy(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        try
        {
            TimepointType studyType = AssayPublishService.get().getTimepointType(study);

            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(getDataRowIdFieldKey().toString(), dataKeys.keySet());
            int rowIndex = 0;
            OntologyObject[] dataRows = Table.select(OntologyManager.getTinfoObject(), Table.ALL_COLUMNS, filter,
                    new Sort(getDataRowIdFieldKey().toString()), OntologyObject.class);

            Map<String, Object>[] dataMaps = new Map[dataRows.length];

            Map<Integer, ExpRun> runCache = new HashMap<Integer, ExpRun>();
            Map<Integer, Map<String, Object>> runPropertyCache = new HashMap<Integer, Map<String, Object>>();

            Set<PropertyDescriptor> typeList = new LinkedHashSet<PropertyDescriptor>();
            typeList.add(createPublishPropertyDescriptor(study, getDataRowIdFieldKey().toString(), PropertyType.INTEGER));
            typeList.add(createPublishPropertyDescriptor(study, "SourceLSID", PropertyType.INTEGER));

            PropertyDescriptor[] runPDs = getPropertyDescriptors(getRunDomain(protocol));
            PropertyDescriptor[] uploadSetPDs = getPropertyDescriptors(getBatchDomain(protocol));

            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            pds.addAll(Arrays.asList(runPDs));
            pds.addAll(Arrays.asList(uploadSetPDs));

            Container sourceContainer = null;

            // little hack here: since the property descriptors created by the 'addProperty' calls below are not in the database,
            // they have no RowId, and such are never equal to each other.  Since the loop below is run once for each row of data,
            // this will produce a types set that contains rowCount*columnCount property descriptors unless we prevent additions
            // to the map after the first row.  This is done by nulling out the 'tempTypes' object after the first iteration:
            Set<PropertyDescriptor> tempTypes = typeList;
            PropertyDescriptor[] rowPropertyDescriptors = getPropertyDescriptors(getRunDataDomain(protocol));
            for (OntologyObject row : dataRows)
            {
                Map<String, Object> dataMap = new HashMap<String, Object>();
                Map<String, ObjectProperty> rowProperties = OntologyManager.getPropertyObjects(row.getContainer(), row.getObjectURI());
                for (PropertyDescriptor pd : rowPropertyDescriptors)
                {
                    // We should skip properties that are set by the resolver: participantID,
                    // and either date or visit, depending on the type of study
                    boolean skipProperty = PARTICIPANTID_PROPERTY_NAME.equals(pd.getName());

                    if (TimepointType.DATE == studyType)
                            skipProperty = skipProperty || DATE_PROPERTY_NAME.equals(pd.getName());
                    else // it's visit-based
                        skipProperty = skipProperty || VISITID_PROPERTY_NAME.equals(pd.getName());

                    if (!skipProperty)
                        addProperty(pd, rowProperties.get(pd.getPropertyURI()), dataMap, tempTypes);
                }

                ExpRun run = runCache.get(row.getOwnerObjectId());
                if (run == null)
                {
                    OntologyObject dataRowParent = OntologyManager.getOntologyObject(row.getOwnerObjectId().intValue());
                    ExpData data = ExperimentService.get().getExpData(dataRowParent.getObjectURI());

                    run = data.getRun();
                    sourceContainer = run.getContainer();
                    runCache.put(row.getOwnerObjectId(), run);
                }

                Map<String, Object> runProperties = runPropertyCache.get(run.getRowId());
                if (runProperties == null)
                {
                    runProperties = OntologyManager.getProperties(run.getContainer(), run.getLSID());
                    runPropertyCache.put(run.getRowId(), runProperties);
                }

                for (PropertyDescriptor pd : pds)
                {
                    if (!TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()) && !PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(pd.getName()))
                    {
                        PropertyDescriptor publishPd = pd.clone();
                        publishPd.setName("Run" + pd.getName());
                        addProperty(publishPd, runProperties.get(pd.getPropertyURI()), dataMap, tempTypes);
                    }
                }

                AssayPublishKey publishKey = dataKeys.get(row.getObjectId());
                dataMap.put("ParticipantID", publishKey.getParticipantId());
                dataMap.put("SequenceNum", publishKey.getVisitId());
                if (TimepointType.DATE == studyType)
                {
                    dataMap.put("Date", publishKey.getDate());
                }
                dataMap.put("SourceLSID", run.getLSID());
                dataMap.put(getDataRowIdFieldKey().toString(), publishKey.getDataId());

                addStandardRunPublishProperties(user, study, tempTypes, dataMap, run);

                dataMaps[rowIndex++] = dataMap;
                tempTypes = null;
            }
            return AssayPublishService.get().publishAssayData(user, sourceContainer, study, protocol.getName(), protocol,
                    dataMaps, new ArrayList<PropertyDescriptor>(typeList), getDataRowIdFieldKey().toString(), errors);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (IOException e)
        {
            errors.add(e.getMessage());
            return null;
        }
        catch (ServletException e)
        {
            errors.add(e.getMessage());
            return null;
        }
    }
}