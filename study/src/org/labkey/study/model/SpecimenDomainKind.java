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
package org.labkey.study.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.SpecimenTablesTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SpecimenDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "Specimen";
    private static final String NAMESPACE_PREFIX = "Specimen";

    private static final String ROWID = "RowId";
    private static final String SPECIMENHASH = "SpecimenHash";
    private static final String PARTICIPANTID = "PTID";
    private static final String VISITDESCRIPTION = "VisitDescription";
    private static final String VISITVALUE = "VisitValue";
    private static final String VOLUMEUNITS = "VolumeUnits";
    private static final String PRIMARYTYPEID = "PrimaryTypeId";
    private static final String ADDITIVETYPEID = "AdditiveTypeId";
    private static final String DERIVATIVETYPEID = "DerivativeTypeId";
    private static final String DERIVATIVETYPEID2 = "DerivativeTypeId2";
    private static final String SUBADDITIVEDERIVATIVE = "SubadditiveDerivative";
    private static final String DRAWTIMESTAMP = "DrawTimestamp";
    private static final String DRAWDATE = "DrawDate";
    private static final String DRAWTIME = "DrawTime";
    private static final String SALRECEIPTDATE = "SalReceiptDate";
    private static final String CLASSID = "ClassId";
    private static final String PROTOCOLNUMBER = "ProtocolNumber";
    private static final String ORIGINATINGLOCATIONID = "OriginatingLocationId";
    private static final String TOTALVOLUME = "TotalVolume";
    private static final String AVAILABLEVOLUME = "AvailableVolume";
    private static final String VIALCOUNT = "VialCount";
    private static final String LOCKEDINREQUESTCOUNT = "LockedInRequestCount";
    private static final String ATREPOSITORYCOUNT = "AtRepositoryCount";
    private static final String AVAILABLECOUNT = "AvailableCount";
    private static final String EXPECTEDAVAILABLECOUNT = "ExpectedAvailableCount";
    private static final String PARTICIPANTSEQUENCENUM = "ParticipantSequenceNum";
    private static final String PROCESSINGLOCATION = "ProcessingLocation";
    private static final String FIRSTPROCESSEDBYINITIALS = "FirstProcessedByInitials";

    private static final List<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> BASE_INDICES;
    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(ROWID, JdbcType.BIGINT, 0, PropertyStorageSpec.Special.PrimaryKey, false, true, null),
            new PropertyStorageSpec(SPECIMENHASH, JdbcType.VARCHAR, 256),
            new PropertyStorageSpec(PARTICIPANTID, JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(VISITDESCRIPTION, JdbcType.VARCHAR, 10),
            new PropertyStorageSpec(VISITVALUE, JdbcType.DECIMAL, 0),
            new PropertyStorageSpec(VOLUMEUNITS, JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(PRIMARYTYPEID, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(ADDITIVETYPEID, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(DERIVATIVETYPEID, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(DERIVATIVETYPEID2, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(SUBADDITIVEDERIVATIVE, JdbcType.VARCHAR, 50),
            new PropertyStorageSpec(DRAWTIMESTAMP, JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(DRAWDATE,  JdbcType.DATE, 0),
            new PropertyStorageSpec(DRAWTIME,  JdbcType.TIME, 0),
            new PropertyStorageSpec(SALRECEIPTDATE, JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(CLASSID, JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(PROTOCOLNUMBER, JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(ORIGINATINGLOCATIONID, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(TOTALVOLUME, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(AVAILABLEVOLUME, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(VIALCOUNT, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(LOCKEDINREQUESTCOUNT, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(ATREPOSITORYCOUNT, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(AVAILABLECOUNT, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(EXPECTEDAVAILABLECOUNT, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(PARTICIPANTSEQUENCENUM, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(PROCESSINGLOCATION, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(FIRSTPROCESSEDBYINITIALS, JdbcType.VARCHAR, 32)
        };

        BASE_PROPERTIES = Arrays.asList(props);

        PropertyStorageSpec.Index[] indices =
        {
            new PropertyStorageSpec.Index(false, SPECIMENHASH),
            new PropertyStorageSpec.Index(false, ADDITIVETYPEID),
            new PropertyStorageSpec.Index(false, DERIVATIVETYPEID),
            new PropertyStorageSpec.Index(false, DERIVATIVETYPEID2),
            new PropertyStorageSpec.Index(false, PRIMARYTYPEID),
            new PropertyStorageSpec.Index(false, ORIGINATINGLOCATIONID),
            new PropertyStorageSpec.Index(false, PARTICIPANTSEQUENCENUM),
            new PropertyStorageSpec.Index(false, PARTICIPANTID)
        };
        BASE_INDICES = new HashSet<>(Arrays.asList(indices));
    }

    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return new HashSet<>(BASE_INDICES);
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return new LinkedHashSet<>(BASE_PROPERTIES);
    }

    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(@Nullable SpecimenTablesTemplate template)
    {
        return null != template ? template.getExtraSpecimenProperties() : Collections.emptySet();
    }
}
