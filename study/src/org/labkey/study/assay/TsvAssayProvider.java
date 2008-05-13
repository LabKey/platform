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
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.security.User;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.TimepointType;
import org.labkey.study.model.StudyManager;

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
        super("GeneralAssayProtocol", "GeneralAssayRun", TsvDataHandler.DATA_TYPE);
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

        Domain dataDomain = PropertyService.get().createDomain(c, "urn:lsid:${LSIDAuthority}:" + ExpProtocol.ASSAY_DOMAIN_DATA + ".Folder-${Container.RowId}:" + ASSAY_NAME_SUBSTITUTION, "Data Fields");
        dataDomain.setDescription("The user is prompted to enter data values for row of data associated with a run, typically done as uploading a file.  This is part of the second step of the upload process.");
        addProperty(dataDomain, SPECIMENID_PROPERTY_NAME,  SPECIMENID_PROPERTY_CAPTION, PropertyType.STRING, "When a matching specimen exists in a study, can be used to identify subject and timepoint for assay. Alternately, supply " + PARTICIPANTID_PROPERTY_NAME + " and either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + ".");
        addProperty(dataDomain, PARTICIPANTID_PROPERTY_NAME, PARTICIPANTID_PROPERTY_CAPTION, PropertyType.STRING, "Used with either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        addProperty(dataDomain, VISITID_PROPERTY_NAME,  VISITID_PROPERTY_CAPTION, PropertyType.DOUBLE, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        addProperty(dataDomain, DATE_PROPERTY_NAME,  DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");

        result.add(dataDomain);
        return result;
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/tsvDataDescription.jsp", form);
    }

    public ExpData getDataForDataRow(Object dataRowId)
    {
        if (!(dataRowId instanceof Integer))
            return null;
        OntologyObject dataRow = OntologyManager.getOntologyObject((Integer) dataRowId);
        if (dataRow == null)
            return null;
        OntologyObject dataRowParent = OntologyManager.getOntologyObject(dataRow.getOwnerObjectId());
        if (dataRowParent == null)
            return null;
        return ExperimentService.get().getExpData(dataRowParent.getObjectURI());
    }

    public ActionURL publish(User user, ExpProtocol protocol, Container study, Set<AssayPublishKey> dataKeys, List<String> errors)
    {
        try
        {
            TimepointType studyType = AssayPublishService.get().getTimepointType(study);

            SimpleFilter filter = new SimpleFilter();
            List<Object> ids = new ArrayList<Object>();
            Map<Object, AssayPublishKey> dataIdToPublishKey = new HashMap<Object, AssayPublishKey>();
            for (AssayPublishKey dataKey : dataKeys)
            {
                ids.add(dataKey.getDataId());
                dataIdToPublishKey.put(dataKey.getDataId(), dataKey);
            }
            filter.addInClause(getDataRowIdFieldKey().toString(), ids);
            int rowIndex = 0;
            OntologyObject[] dataRows = Table.select(OntologyManager.getTinfoObject(), Table.ALL_COLUMNS, filter,
                    new Sort(getDataRowIdFieldKey().toString()), OntologyObject.class);

            Map<String, Object>[] dataMaps = new Map[dataRows.length];
            List<PropertyDescriptor> typeList = new ArrayList<PropertyDescriptor>();

            Map<Integer, ExpRun> runCache = new HashMap<Integer, ExpRun>();
            Map<Integer, Map<String, Object>> runPropertyCache = new HashMap<Integer, Map<String, Object>>();

            typeList.add(createPublishPropertyDescriptor(study, getDataRowIdFieldKey().toString(), getDataRowIdType()));
            typeList.add(createPublishPropertyDescriptor(study, "SourceLSID", getDataRowIdType()));

            PropertyDescriptor[] runPDs = getRunPropertyColumns(protocol);
            PropertyDescriptor[] uploadSetPDs = getUploadSetColumns(protocol);

            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            pds.addAll(Arrays.asList(runPDs));
            pds.addAll(Arrays.asList(uploadSetPDs));

            Container sourceContainer = null;
            for (OntologyObject row : dataRows)
            {
                boolean hasDateColumn = false;
                Map<String, Object> dataMap = new HashMap<String, Object>();
                Map<String, Object> rowProperties = OntologyManager.getProperties(row.getContainer(), row.getObjectURI());
                PropertyDescriptor[] rowPropertyDescriptors = getRunDataColumns(protocol);
                for (PropertyDescriptor pd : rowPropertyDescriptors)
                {
                    if (!PARTICIPANTID_PROPERTY_NAME.equals(pd.getName()) && !VISITID_PROPERTY_NAME.equals(pd.getName()) && !DATE_PROPERTY_NAME.equals(pd.getName()))
                        addProperty(pd, rowProperties.get(pd.getPropertyURI()), dataMap, typeList);
                    hasDateColumn = hasDateColumn || DATE_PROPERTY_NAME.equals(pd.getName());
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
                        publishPd.setName("Run " + pd.getName());
                        addProperty(publishPd, runProperties.get(pd.getPropertyURI()), dataMap, typeList);
                    }
                }

                AssayPublishKey publishKey = dataIdToPublishKey.get(row.getObjectId());
                dataMap.put("ParticipantID", publishKey.getParticipantId());
                dataMap.put("SequenceNum", publishKey.getVisitId());
                if (TimepointType.DATE == studyType)
                {
                    dataMap.put("Date", publishKey.getDate());
                }
                dataMap.put("SourceLSID", run.getLSID());
                dataMap.put(getDataRowIdFieldKey().toString(), publishKey.getDataId());
                addProperty(study, "Run Name", run.getName(), dataMap, typeList);
                addProperty(study, "Run Comments", run.getComments(), dataMap, typeList);
                addProperty(study, "Run CreatedOn", run.getCreated(), dataMap, typeList);
                User createdBy = run.getCreatedBy();
                addProperty(study, "Run CreatedBy", createdBy == null ? null : createdBy.getDisplayName(HttpView.currentContext()), dataMap, typeList);
                dataMaps[rowIndex++] = dataMap;
            }
            return AssayPublishService.get().publishAssayData(user, sourceContainer, study, protocol.getName(), protocol,
                    dataMaps, typeList, getDataRowIdFieldKey().toString(), errors);
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

    public TableInfo createDataTable(QuerySchema schema, String alias, ExpProtocol protocol)
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
