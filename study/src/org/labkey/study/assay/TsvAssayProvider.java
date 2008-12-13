/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.exp.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.api.*;
import org.labkey.api.data.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.security.User;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.TimepointType;

import javax.servlet.ServletException;
import java.util.*;
import java.sql.SQLException;
import java.io.File;
import java.io.IOException;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:39 AM
 */
public class TsvAssayProvider extends AbstractAssayProvider
{
    public TsvAssayProvider()
    {
        this("GeneralAssayProtocol", "GeneralAssayRun");
    }

    protected TsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, TsvDataHandler.DATA_TYPE);
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles)
    {
        List<AssayDataCollector> result = super.getDataCollectors(uploadedFiles);
        result.add(0, new TextAreaDataCollector());
        return result;
    }

    public String getName()
    {
        return "General";
    }

    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> result = super.createDefaultDomains(c, user);

        Domain dataDomain = createDataDomain(c, user);
        if (dataDomain != null)
            result.add(dataDomain);
        return result;
    }

    protected Domain createDataDomain(Container c, User user)
    {
        Domain dataDomain = PropertyService.get().createDomain(c, "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + ExpProtocol.ASSAY_DOMAIN_DATA + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":" + ASSAY_NAME_SUBSTITUTION, "Data Fields");
        dataDomain.setDescription("The user is prompted to enter data values for row of data associated with a run, typically done as uploading a file.  This is part of the second step of the upload process.");
        addProperty(dataDomain, SPECIMENID_PROPERTY_NAME,  SPECIMENID_PROPERTY_CAPTION, PropertyType.STRING, "When a matching specimen exists in a study, can be used to identify subject and timepoint for assay. Alternately, supply " + PARTICIPANTID_PROPERTY_NAME + " and either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + ".");
        addProperty(dataDomain, PARTICIPANTID_PROPERTY_NAME, PARTICIPANTID_PROPERTY_CAPTION, PropertyType.STRING, "Used with either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        addProperty(dataDomain, VISITID_PROPERTY_NAME,  VISITID_PROPERTY_CAPTION, PropertyType.DOUBLE, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        addProperty(dataDomain, DATE_PROPERTY_NAME,  DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        return dataDomain;
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/tsvDataDescription.jsp", form);
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

        OntologyObject dataRow = OntologyManager.getOntologyObject(id);
        if (dataRow == null)
            return null;
        OntologyObject dataRowParent = OntologyManager.getOntologyObject(dataRow.getOwnerObjectId());
        if (dataRowParent == null)
            return null;
        return ExperimentService.get().getExpData(dataRowParent.getObjectURI());
    }

    public ActionURL publish(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
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
            typeList.add(createPublishPropertyDescriptor(study, getDataRowIdFieldKey().toString(), getDataRowIdType()));
            typeList.add(createPublishPropertyDescriptor(study, "SourceLSID", getDataRowIdType()));

            PropertyDescriptor[] runPDs = getRunPropertyColumns(protocol);
            PropertyDescriptor[] uploadSetPDs = getUploadSetColumns(protocol);

            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            pds.addAll(Arrays.asList(runPDs));
            pds.addAll(Arrays.asList(uploadSetPDs));

            Container sourceContainer = null;

            // little hack here: since the property descriptors created by the 'addProperty' calls below are not in the database,
            // they have no RowId, and such are never equal to each other.  Since the loop below is run once for each row of data,
            // this will produce a types set that contains rowCount*columnCount property descriptors unless we prevent additions
            // to the map after the first row.  This is done by nulling out the 'tempTypes' object after the first iteration:
            Set<PropertyDescriptor> tempTypes = typeList;
            for (OntologyObject row : dataRows)
            {
                Map<String, Object> dataMap = new HashMap<String, Object>();
                Map<String, Object> rowProperties = OntologyManager.getProperties(row.getContainer(), row.getObjectURI());
                PropertyDescriptor[] rowPropertyDescriptors = getRunDataColumns(protocol);
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
                    OntologyObject dataRowParent = OntologyManager.getOntologyObject(row.getOwnerObjectId());
                    ExpData data = ExperimentService.get().getExpData(dataRowParent.getObjectURI());

                    run = data.getRun();
                    sourceContainer = run.getContainer();
                    runCache.put(row.getOwnerObjectId(), run);
                }

                Map<String, Object> runProperties = runPropertyCache.get(run.getRowId());
                if (runProperties == null)
                {
                    runProperties = OntologyManager.getProperties(run.getContainer().getId(), run.getLSID());
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

                addStandardRunPublishProperties(study, tempTypes, dataMap, run);

                dataMaps[rowIndex++] = dataMap;
                tempTypes = null;
            }
            return AssayPublishService.get().publishAssayData(user, sourceContainer, study, protocol.getName(), protocol,
                    dataMaps, new ArrayList<PropertyDescriptor>(typeList), getDataRowIdFieldKey().toString(), errors);
        }
        catch (SQLException e)
        {
            errors.add(e.getMessage());
            return null;
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

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Arrays.asList(new StudyParticipantVisitResolverType(), new ThawListResolverType());
    }

    public TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol)
    {
        return new RunDataTable(schema, alias, protocol);
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return FieldKey.fromParts("Properties", PARTICIPANTID_PROPERTY_NAME);
    }

    public FieldKey getVisitIDFieldKey(Container targetStudy)
    {
        if (AssayPublishService.get().getTimepointType(targetStudy) == TimepointType.VISIT)
            return FieldKey.fromParts("Properties", VISITID_PROPERTY_NAME);
        else
            return FieldKey.fromParts("Properties", DATE_PROPERTY_NAME);
    }

    public FieldKey getRunIdFieldKeyFromDataRow()
    {
        return FieldKey.fromParts("Run", "RowId");
    }

    public FieldKey getDataRowIdFieldKey()
    {
        return FieldKey.fromParts("ObjectId");
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return FieldKey.fromParts("Properties", SPECIMENID_PROPERTY_NAME);
    }

    protected PropertyType getDataRowIdType()
    {
        return PropertyType.INTEGER;
    }

    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        // intentionally do NOT require any columns exist for a TSV-based assay:
        return Collections.emptyMap();
    }
}
