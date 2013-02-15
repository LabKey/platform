/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.geomicroarray;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.geomicroarray.query.GEOMicroarrayProviderSchema;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GEOMicroarrayAssayProvider extends AbstractAssayProvider
{
    public static final String NAME = "GEOMicroarray";
    public static final String LSID_PREFIX = "GEOMicroarray";
    public static final AssayDataType GEOMICROARRAY_DATA_TYPE = new AssayDataType(LSID_PREFIX, new FileType(".tsv"));

    public static final GEOMicroarrayColumnMappingProperty FEATURE_ANNOTATION_SET_NAME_COLUMN = new GEOMicroarrayColumnMappingProperty("name", "Feature Annotation Set Name", true);

    public GEOMicroarrayAssayProvider()
    {
        super(LSID_PREFIX, LSID_PREFIX, GEOMICROARRAY_DATA_TYPE);
    }

    @Override @NotNull
    public AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol)
    {
        return new AssayTableMetadata(this, protocol, null, FieldKey.fromParts("RunId"), FieldKey.fromParts("RowId"));
    }

    @Override
    public ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new HtmlView("");
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        return super.createBatchDomain(c, user, false);
    }

    @Override
    public AssayProtocolSchema createProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        return null;
//        return new GEOMicroArrayProtocolSchema(user, container, protocol, targetStudy);
    }

    @Override
    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Collections.emptyList();
    }

    @Override
    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public PipelineProvider getPipelineProvider()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "TODO"; // TODO
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = super.createRunDomain(c, user);
        Domain runDomain = result.getKey();
        Container lookupContainer = c.getProject();

        DomainProperty featureSet = addProperty(runDomain, FEATURE_ANNOTATION_SET_NAME_COLUMN.getName(), FEATURE_ANNOTATION_SET_NAME_COLUMN.getLabel(), PropertyType.STRING);
        featureSet.setLookup(new Lookup(lookupContainer, AssaySchema.NAME + "." + getResourceName(), GEOMicroarrayProviderSchema.FEATURE_ANNOTATION_SET_TABLE_NAME));
        featureSet.setShownInInsertView(false);
        featureSet.setShownInUpdateView(false);
        featureSet.setRequired(true);

        return result;
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = super.getRequiredDomainProperties();

        Set<String> runProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_RUN);
        if (runProperties == null)
        {
            runProperties = new HashSet<String>();
            domainMap.put(ExpProtocol.ASSAY_DOMAIN_RUN, runProperties);
        }

        runProperties.add(FEATURE_ANNOTATION_SET_NAME_COLUMN.getName());

        return domainMap;
    }

    @Override
    public AssayProviderSchema createProviderSchema(User user, Container container, Container targetStudy)
    {
        return new GEOMicroarrayProviderSchema(user, container, this, targetStudy, false);
    }
}
