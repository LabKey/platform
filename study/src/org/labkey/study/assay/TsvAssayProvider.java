/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.study.StudyModule;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.util.*;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:39 AM
 */
public class TsvAssayProvider extends AbstractTsvAssayProvider
{
    private static final Set<String> participantImportAliases;
    private static final Set<String> specimenImportAliases;
    private static final Set<String> visitImportAliases;
    private static final Set<String> dateImportAliases;

    static
    {
        // this is the static lists of import aliases used in the default template
        participantImportAliases = PageFlowUtil.set("ptid", "participantId");
        specimenImportAliases = PageFlowUtil.set("specId", "vialId", "vialId1", "vial1_id", "guspec");
        visitImportAliases = PageFlowUtil.set("visitNo", "visit_no");
        dateImportAliases = PageFlowUtil.set("drawDt", "draw_date", "drawDate");
    }

    public TsvAssayProvider()
    {
        this("GeneralAssayProtocol", "GeneralAssayRun");
    }

    protected TsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, TsvDataHandler.DATA_TYPE, new AssayTableMetadata(
            null,
            FieldKey.fromParts("Run"),
            FieldKey.fromParts(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME)));
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        List<AssayDataCollector> result = super.getDataCollectors(uploadedFiles, context);
        if (PipelineDataCollector.getFileQueue(context).isEmpty())
        {
            result.add(0, new TextAreaDataCollector());
        }
        return result;
    }

    public String getName()
    {
        return "General";
    }

    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = super.createDefaultDomains(c, user);

        Pair<Domain, Map<DomainProperty, Object>> resultDomain = createResultDomain(c, user);
        if (resultDomain != null)
            result.add(resultDomain);
        return result;
    }

    protected Pair<Domain,Map<DomainProperty,Object>> createResultDomain(Container c, User user)
    {
        Domain dataDomain = PropertyService.get().createDomain(c, "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + ExpProtocol.ASSAY_DOMAIN_DATA + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":" + ASSAY_NAME_SUBSTITUTION, "Data Fields");
        dataDomain.setDescription("The user is prompted to enter data values for row of data associated with a run, typically done as uploading a file.  This is part of the second step of the upload process.");
        DomainProperty specimenID = addProperty(dataDomain, SPECIMENID_PROPERTY_NAME,  SPECIMENID_PROPERTY_CAPTION, PropertyType.STRING, "When a matching specimen exists in a study, can be used to identify subject and timepoint for assay. Alternately, supply " + PARTICIPANTID_PROPERTY_NAME + " and either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + ".");
        specimenID.setImportAliasSet(specimenImportAliases);

        DomainProperty participantID = addProperty(dataDomain, PARTICIPANTID_PROPERTY_NAME, PARTICIPANTID_PROPERTY_CAPTION, PropertyType.STRING, "Used with either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        participantID.setImportAliasSet(participantImportAliases);

        DomainProperty visitID = addProperty(dataDomain, VISITID_PROPERTY_NAME,  VISITID_PROPERTY_CAPTION, PropertyType.DOUBLE, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        visitID.setImportAliasSet(visitImportAliases);

        DomainProperty dateProperty = addProperty(dataDomain, DATE_PROPERTY_NAME,  DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        dateProperty.setImportAliasSet(dateImportAliases);

        return new Pair<Domain, Map<DomainProperty, Object>>(dataDomain, Collections.<DomainProperty, Object>emptyMap());
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/tsvDataDescription.jsp", form);
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Arrays.asList(new StudyParticipantVisitResolverType(), new ThawListResolverType());
    }

    public AssayResultTable createDataTable(AssaySchema schema, ExpProtocol protocol, boolean includeCopiedToStudyColumns)
    {
        return new AssayResultTable(schema, protocol, this, includeCopiedToStudyColumns);
    }

    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        // intentionally do NOT require any columns exist for a TSV-based assay:
        return Collections.emptyMap();
    }

    @Override
    public boolean hasUsefulDetailsPage()
    {
        return false;
    }

    public String getDescription()
    {
        return "Imports data from simple Excel or TSV files.";
    }

    @Override
    public DataExchangeHandler createDataExchangeHandler()
    {
        return new TsvDataExchangeHandler();
    }

    public PipelineProvider getPipelineProvider()
    {
        return new AssayPipelineProvider(StudyModule.class,
                new PipelineProvider.FileTypesEntryFilter(getDataType().getFileType()), 
                this, "Import Text or Excel Assay");
    }

    @Override
    public Class<? extends Controller> getDataImportAction()
    {
        return TsvImportAction.class;
    }
}
