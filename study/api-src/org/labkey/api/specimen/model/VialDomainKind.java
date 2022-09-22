/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.specimen.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VialDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "Vial";
    private static final String NAMESPACE_PREFIX = "Vial";

    private static final String ROWID = "RowId";
    private static final String SPECIMENHASH = "SpecimenHash";
    private static final String VOLUME = "Volume";
    private static final String GLOBALUNIQUEID = "GlobalUniqueId";
    private static final String REQUESTABLE = "Requestable";
    private static final String CURRENTLOCATION = "CurrentLocation";
    private static final String PRIMARYVOLUME = "PrimaryVolume";
    private static final String PRIMARYVOLUMEUNITS = "PrimaryVolumeUnits";
    private static final String TOTALCELLCOUNT = "TotalCellCount";
    private static final String SPECIMENID = "SpecimenId";
    private static final String LOCKEDINREQUEST = "LockedInRequest";
    private static final String ATREPOSITORY = "AtRepository";
    private static final String AVAILABLE = "Available";
    private static final String LATESTCOMMENTS = "LatestComments";
    private static final String LATESTQUALITYCOMMENTS = "LatestQualityComments";
    private static final String AVAILABILITYREASON = "AvailabilityReason";
    private static final String PROCESSINGLOCATION = "ProcessingLocation";
    private static final String FIRSTPROCESSEDBYINITIALS = "FirstProcessedByInitials";
    private static final String TUBETYPE = "TubeType";

    private static final List<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> BASE_INDICES;
    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(ROWID, JdbcType.BIGINT, 0, PropertyStorageSpec.Special.PrimaryKey, false, false, null),         // TODO why is this not auto-incr???
            new PropertyStorageSpec(GLOBALUNIQUEID, JdbcType.VARCHAR, 50, false, null),
            new PropertyStorageSpec(VOLUME, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(SPECIMENHASH, JdbcType.VARCHAR, 256),
            new PropertyStorageSpec(REQUESTABLE, JdbcType.BOOLEAN, 0),
            new PropertyStorageSpec(CURRENTLOCATION, JdbcType.INTEGER, 0, true, null),
            new PropertyStorageSpec(ATREPOSITORY, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(LOCKEDINREQUEST, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(AVAILABLE, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(PROCESSINGLOCATION, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(SPECIMENID, JdbcType.BIGINT, 0, false, null),
            new PropertyStorageSpec(PRIMARYVOLUME, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(PRIMARYVOLUMEUNITS, JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(FIRSTPROCESSEDBYINITIALS, JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(AVAILABILITYREASON, JdbcType.VARCHAR, 256),
            new PropertyStorageSpec(TOTALCELLCOUNT, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(LATESTCOMMENTS, JdbcType.VARCHAR, 500),
            new PropertyStorageSpec(LATESTQUALITYCOMMENTS, JdbcType.VARCHAR, 500),
            new PropertyStorageSpec(TUBETYPE, JdbcType.VARCHAR, 64, "The type of vial.")
        };
        BASE_PROPERTIES = Arrays.asList(props);

        PropertyStorageSpec.Index[] indices =
        {
            new PropertyStorageSpec.Index(false, SPECIMENHASH),
            new PropertyStorageSpec.Index(false, CURRENTLOCATION),
            new PropertyStorageSpec.Index(true, GLOBALUNIQUEID),
            new PropertyStorageSpec.Index(false, SPECIMENID),
            new PropertyStorageSpec.Index(false, SPECIMENID, PROCESSINGLOCATION),
            new PropertyStorageSpec.Index(false, SPECIMENID, FIRSTPROCESSEDBYINITIALS)
        };
        BASE_INDICES = new HashSet<>(Arrays.asList(indices));

    }

    final private String _specimenDomainURI;

    public VialDomainKind()
    {
        this(null);
    }

    public VialDomainKind(String specimenDomainURI)
    {
        super();
        _specimenDomainURI = specimenDomainURI;
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return new LinkedHashSet<>(BASE_PROPERTIES);
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return new HashSet<>(BASE_INDICES);
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container, SpecimenTablesProvider provider)
    {
        Set<PropertyStorageSpec.ForeignKey> foreignKeys = new HashSet<>();
        foreignKeys.add(new PropertyStorageSpec.ForeignKey(CURRENTLOCATION, "study", "Site", "RowId", null, true));
        foreignKeys.add(new PropertyStorageSpec.ForeignKey(SPECIMENID, "study", "Specimen", "RowId", _specimenDomainURI, true));
        setForeignKeyTableInfos(container, foreignKeys, provider);
        return foreignKeys;
    }

    @Override
    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(@Nullable SpecimenTablesTemplate template)
    {
        return null != template ? template.getExtraVialProperties() : Collections.emptySet();
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain);
    }

    @Override
    public @NotNull ValidationException updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update,
                                                     @Nullable JSONObject options, Container container, User user, boolean includeWarnings){
        ValidationException exception;
        try (var transaction = SpecimenSchema.get().getScope().ensureTransaction())
        {
            exception = new ValidationException();
            SpecimenTablesProvider stp = new SpecimenTablesProvider(container, user, null);
            Domain domainSpecimen = stp.getDomain("specimen", false);
            Domain domainVial = stp.getDomain("vial", false);

            // Check for the same name in Specimen and Vial
            CaseInsensitiveHashSet specimenFields = new CaseInsensitiveHashSet();
            if (null != domainSpecimen)
            {
                for (DomainProperty prop : domainSpecimen.getProperties())
                {
                    if (null != prop.getName())
                    {
                        specimenFields.add(prop.getName().toLowerCase());
                    }
                }
            }

            Set<String> mandatoryPropertyNames = getMandatoryPropertyNames(domainVial);
            List<PropertyDescriptor> optionalVialFields = new ArrayList<>();
            for (GWTPropertyDescriptor prop : update.getFields())
            {
                if (null != prop.getName())
                {
                    if (!mandatoryPropertyNames.contains(prop.getName()))
                    {
                        if (specimenFields.contains(prop.getName().toLowerCase()))
                        {
                            exception.addError(new PropertyValidationError("Vial cannot have a custom field of the same name as a Specimen field.", prop.getName(), prop.getPropertyId()));
                        }

                        optionalVialFields.add(getPropFromGwtProp(prop));
                        if (prop.getName().contains(" "))
                        {
                            exception.addError(new PropertyValidationError("Name '" + prop.getName() + "' should not contain spaces.", prop.getName(), prop.getPropertyId()));
                        }
                    }
                }
            }

            exception = checkRollups(optionalVialFields, null, container, user, exception, includeWarnings);
            exception.addErrors(super.updateDomain(original, update, options, container, user, includeWarnings));

            if (!exception.hasErrors())
            {
                transaction.commit();
            }
            return exception;
        }
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        Set<String> names = new HashSet<>();
        names.add(COMMENTS);
        names.add(COLUMN);
        return names;
    }
}
