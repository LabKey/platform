/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AbstractTsvAssayProvider;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunCreator;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.actions.PlateUploadForm;
import org.labkey.api.data.Container;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.Module;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.SampleMetadataInputFormat;
import org.labkey.api.study.assay.StudyParticipantVisitResolverType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.InsertView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Sep 27, 2007
 * Time: 3:26:44 PM
 */
public abstract class AbstractPlateBasedAssayProvider extends AbstractTsvAssayProvider implements PlateBasedAssayProvider
{
    public static final String ASSAY_DOMAIN_SAMPLE_WELLGROUP = ExpProtocol.ASSAY_DOMAIN_PREFIX + "SampleWellGroup";
    public static final String SAMPLE_METADATA_INPUT_ROLE = "Sample Metadata";
    public static final String METADATA_INPUT_FORMAT_SUFFIX = "#SampleMetadataInputFormat";
    public static final String VIRUS_WELL_GROUP_NAME = "VirusWellGroupName";

    public AbstractPlateBasedAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, String resultRowLsidPrefix, AssayDataType dataType, Module declaringModule)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, resultRowLsidPrefix, dataType, declaringModule);
    }

    @Override
    public void setPlateTemplate(Container container, ExpProtocol protocol, PlateTemplate template)
    {
        if (!isPlateBased())
            throw new IllegalStateException("Only plate-based assays may store a plate template.");
        Map<String, ObjectProperty> props = new HashMap<>(protocol.getObjectProperties());
        ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(),
                protocol.getLSID() + "#PlateTemplate", template.getName());
        props.put(prop.getPropertyURI(), prop);
        protocol.setObjectProperties(props);
    }

    @Override
    public PlateTemplate getPlateTemplate(Container container, ExpProtocol protocol)
    {
        ObjectProperty prop = protocol.getObjectProperties().get(protocol.getLSID() + "#PlateTemplate");
        return prop != null ? PlateService.get().getPlateTemplate(protocol.getContainer(), prop.getStringValue()) : null;
    }

    public boolean isPlateBased()
    {
        return true;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createSampleWellGroupDomain(Container c, User user)
    {
        String domainLsid = getPresubstitutionLsid(ASSAY_DOMAIN_SAMPLE_WELLGROUP);
        Domain sampleWellGroupDomain = PropertyService.get().createDomain(c, domainLsid, "Sample Fields");
        sampleWellGroupDomain.setDescription("Define the sample fields for this assay design. The user will be prompted to enter these fields for each of the sample well groups in their chosen plate template.");
        return new Pair<>(sampleWellGroupDomain, Collections.emptyMap());
    }

    @Override
    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = super.createDefaultDomains(c, user);
        result.add(createSampleWellGroupDomain(c, user));
        return result;
    }

    @Override
    public AssayRunCreator getRunCreator()
    {
        return new PlateBasedRunCreator(this);
    }

    @Override
    public File getSampleMetadataFile(Container container, int runId)
    {
        ExpRun run = ExperimentService.get().getExpRun(runId);
        if (!run.getContainer().equals(container))
            return null;
        if (getMetadataInputFormat(run.getProtocol()) == SampleMetadataInputFormat.MANUAL)
            return null;
        AssayProvider provider = AssayService.get().getProvider(run.getProtocol());
        if (!(provider instanceof AbstractPlateBasedAssayProvider))
            return null;

        Map<ExpMaterial, String> materials = run.getMaterialInputs();
        if (materials.isEmpty())
            return null;

        ExpMaterial firstMaterial = materials.entrySet().iterator().next().getKey();

        ExpRun sampleDerivationRun = firstMaterial.getRun();

        Map<ExpData, String> sampleDerivationInputs = sampleDerivationRun.getDataInputs();

        for (Map.Entry<ExpData, String> entry : sampleDerivationInputs.entrySet())
        {
            if (SAMPLE_METADATA_INPUT_ROLE.equals(entry.getValue()))
                return entry.getKey().getFile();
        }
        return null;
    }

    @Override
    public Domain getSampleWellGroupDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ASSAY_DOMAIN_SAMPLE_WELLGROUP);
    }

    @Override
    public SampleMetadataInputFormat[] getSupportedMetadataInputFormats()
    {
        return new SampleMetadataInputFormat[]{SampleMetadataInputFormat.MANUAL};
    }

    protected SampleMetadataInputFormat getDefaultMetadataInputFormat()
    {
        return SampleMetadataInputFormat.MANUAL;
    }

    @Override
    public SampleMetadataInputFormat getMetadataInputFormat(ExpProtocol protocol)
    {
        ObjectProperty prop = protocol.getObjectProperties().get(protocol.getLSID() + METADATA_INPUT_FORMAT_SUFFIX);
        if (prop != null)
        {
            SampleMetadataInputFormat format = SampleMetadataInputFormat.valueOf(prop.getStringValue());

            if (format != null)
                return format;
        }
        return getDefaultMetadataInputFormat();
    }

    @Override
    public void setMetadataInputFormat(ExpProtocol protocol, SampleMetadataInputFormat format) throws ExperimentException
    {
        for (SampleMetadataInputFormat inputFormat : getSupportedMetadataInputFormats())
        {
            if (format == inputFormat)
            {
                Map<String, ObjectProperty> props = new HashMap<>(protocol.getObjectProperties());
                String propertyURI = protocol.getLSID() + METADATA_INPUT_FORMAT_SUFFIX;
                ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(), propertyURI, format.name());
                props.put(propertyURI, prop);

                protocol.setObjectProperties(props);
                return;
            }
        }
        throw new ExperimentException("This assay protocol does not support the specified metadata input format : " + format.name());
    }

    @Override
    public PlateSamplePropertyHelper getSamplePropertyHelper(PlateUploadForm context, ParticipantVisitResolverType filterInputsForType)
    {
        // Re-use the same PlateSamplePropertyHelper so it's able to utilize member variables across calls.
        PlateSamplePropertyHelper helper = context.getSamplePropertyHelper();
        PlateTemplate template = getPlateTemplate(context.getContainer(), context.getProtocol());
        Domain sampleDomain = getSampleWellGroupDomain(context.getProtocol());
        List<? extends DomainProperty> allSampleProperties = sampleDomain.getProperties();
        List<? extends DomainProperty> selectedSampleProperties = allSampleProperties;
        if (filterInputsForType != null)
        {
            List<DomainProperty> selected = new ArrayList<>();
            for (DomainProperty possible : allSampleProperties)
            {
                if (filterInputsForType.collectPropertyOnUpload(context, possible.getName()))
                    selected.add(possible);
            }
            selectedSampleProperties = selected;
        }
        // If we don't have a sample helper yet, create it here:
        if (helper == null)
        {
            helper = createSampleFilePropertyHelper(context.getContainer(), context.getProtocol(), selectedSampleProperties, template, ((PlateBasedAssayProvider)context.getProvider()).getMetadataInputFormat(context.getProtocol()));
            context.setSamplePropertyHelper(helper);
        }
        else
        {
            // We already have a sample helper, but the desired domain properties may have changed:
            helper.setDomainProperties(selectedSampleProperties);
        }
        return helper;
    }

    protected PlateSamplePropertyHelper createSampleFilePropertyHelper(Container c, ExpProtocol protocol, List<? extends DomainProperty> sampleProperties, PlateTemplate template, SampleMetadataInputFormat inputFormat)
    {
        if (inputFormat == SampleMetadataInputFormat.MANUAL)
            return new PlateSamplePropertyHelper(sampleProperties, template);
        else
            return new PlateSampleFilePropertyHelper(c, protocol, sampleProperties, template, inputFormat);
    }

    /**
     * Resolves a plate reader instance from a reader name
     */
    @Override
    public PlateReader getPlateReader(String readerName)
    {
        return null;
    }

    @Override
    public Collection<StatsService.CurveFitType> getCurveFits()
    {
        return Collections.emptyList();
    }

    public static class SpecimenIDLookupResolverType extends StudyParticipantVisitResolverType
    {
        // null means we haven't checked the request yet to know whether
        // or not to include the data
        private Boolean includeParticipantAndVisit = null;

        private static final String INCLUDE_PARTICIPANT_AND_VISIT = "includeParticipantAndVisit";

        @Override
        public String getName()
        {
            return "SpecimenID";
        }

        @Override
        public String getDescription()
        {
            return "Specimen/sample id.";
        }

        @Override
        public void render(RenderContext ctx) throws Exception
        {
            HtmlView view = new HtmlView(
                    "<input type=\"checkbox\" name=\"" +
                            INCLUDE_PARTICIPANT_AND_VISIT +
                            "\">I will also provide participant id and visit id");
            view.render(ctx.getRequest(), ctx.getViewContext().getResponse());
        }

        @Override
        public boolean collectPropertyOnUpload(AssayRunUploadContext<?> uploadContext, String propertyName)
        {
            if (propertyName.equals(AbstractAssayProvider.DATE_PROPERTY_NAME))
                return false;

            if (propertyName.equals(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) ||
                propertyName.equals(AbstractAssayProvider.VISITID_PROPERTY_NAME))
            {
                if (includeParticipantAndVisit == null)
                {
                    // Need to initialize it
                    String param = uploadContext.getRequest().getParameter(INCLUDE_PARTICIPANT_AND_VISIT);
                    if ("on".equals(param))
                        includeParticipantAndVisit = Boolean.TRUE;
                    else
                        includeParticipantAndVisit = Boolean.FALSE;
                }
                return includeParticipantAndVisit.booleanValue();
            }
            return true;
        }

        @Override
        public void addHiddenFormFields(AssayRunUploadContext<?> form, InsertView view)
        {
            view.getDataRegion().addHiddenFormField(INCLUDE_PARTICIPANT_AND_VISIT,
                    form.getRequest().getParameter(INCLUDE_PARTICIPANT_AND_VISIT));
        }
    }

    public static class ParticipantVisitLookupResolverType extends StudyParticipantVisitResolverType
    {
        @Override
        public String getName()
        {
            return "ParticipantVisit";
        }

        @Override
        public String getDescription()
        {
            return "Participant id and visit id.";
        }

        @Override
        public boolean collectPropertyOnUpload(AssayRunUploadContext<?> uploadContext, String propertyName)
        {
            return !(propertyName.equals(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) ||
                    propertyName.equals(AbstractAssayProvider.DATE_PROPERTY_NAME));
        }
    }

    public static class ParticipantDateLookupResolverType extends StudyParticipantVisitResolverType
    {
        @Override
        public String getName()
        {
            return "ParticipantDate";
        }

        @Override
        public String getDescription()
        {
            return "Participant id and date.";
        }

        @Override
        public boolean collectPropertyOnUpload(AssayRunUploadContext<?> uploadContext, String propertyName)
        {
            return !(propertyName.equals(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) ||
                    propertyName.equals(AbstractAssayProvider.VISITID_PROPERTY_NAME));
        }
    }

    public static class ParticipantVisitDateLookupResolverType extends StudyParticipantVisitResolverType
    {
        @Override
        public String getName()
        {
            return "ParticipantVisitDate";
        }

        @Override
        public String getDescription()
        {
            return "Participant id, visit id, and date.";
        }

        @Override
        public boolean collectPropertyOnUpload(AssayRunUploadContext<?> uploadContext, String propertyName)
        {
            return !(propertyName.equals(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME));
        }
    }

    public static class CurveFitTableInfo extends EnumTableInfo
    {
        PlateBasedAssayProvider _provider;

        public CurveFitTableInfo(UserSchema schema, PlateBasedAssayProvider provider, String description)
        {
            super(StatsService.CurveFitType.class, schema, description, false);
            _provider = provider;
        }

        @Override
        public @NotNull SQLFragment getFromSQL()
        {
            checkReadBeforeExecute();
            SQLFragment sql = new SQLFragment();
            String separator = "";
            EnumSet<StatsService.CurveFitType> enumSet = EnumSet.allOf(_enum);
            for (StatsService.CurveFitType e : enumSet)
            {
                if (_provider.getCurveFits().contains(e))
                {
                    sql.append(separator);
                    separator = " UNION ";
                    sql.append("SELECT ? AS VALUE, ? AS RowId, ? As Ordinal");
                    sql.add(_valueGetter.getValue(e));
                    sql.add(_rowIdGetter.getRowId(e));
                    sql.add(e.ordinal());
                }
            }
            return sql;
        }
    }
}
