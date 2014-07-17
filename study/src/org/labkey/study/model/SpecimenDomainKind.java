/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.study.SpecimenTablesTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SpecimenDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "Specimen";
    private static final String NAMESPACE_PREFIX = "Specimen";

    private final static String ROWID = "RowId";
    private final static String SPECIMENHASH = "SpecimenHash";
    private final static String PARTICIPANTID = "PTID";
    private final static String VISITDESCRIPTION = "VisitDescription";
    private final static String VISITVALUE = "VisitValue";
    private final static String VOLUMEUNITS = "VolumeUnits";
    private final static String PRIMARYTYPEID = "PrimaryTypeId";
    private final static String ADDITIVETYPEID = "AdditiveTypeId";
    private final static String DERIVATIVETYPEID = "DerivativeTypeId";
    private final static String DERIVATIVETYPEID2 = "DerivativeTypeId2";
    private final static String SUBADDITIVEDERIVATIVE = "SubadditiveDerivative";
    private final static String DRAWTIMESTAMP = "DrawTimestamp";
    private static final String DRAWDATE = "DrawDate";
    private static final String DRAWTIME = "DrawTime";
    private final static String SALRECEIPTDATE = "SalReceiptDate";
    private final static String CLASSID = "ClassId";
    private final static String PROTOCOLNUMBER = "ProtocolNumber";
    private final static String ORIGINATINGLOCATIONID = "OriginatingLocationId";
    private final static String TOTALVOLUME = "TotalVolume";
    private final static String AVAILABLEVOLUME = "AvailableVolume";
    private final static String VIALCOUNT = "VialCount";
    private final static String LOCKEDINREQUESTCOUNT = "LockedInRequestCount";
    private final static String ATREPOSITORYCOUNT = "AtRepositoryCount";
    private final static String AVAILABLECOUNT = "AvailableCount";
    private final static String EXPECTEDAVAILABLECOUNT = "ExpectedAvailableCount";
    private final static String PARTICIPANTSEQUENCENUM = "ParticipantSequenceNum";
    private final static String PROCESSINGLOCATION = "ProcessingLocation";
    private final static String FIRSTPROCESSEDBYINITIALS = "FirstProcessedByInitials";

    private final static List<PropertyStorageSpec> BASE_PROPERTIES;
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
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        Set<PropertyStorageSpec.Index> indices = new HashSet<>(BASE_INDICES);
        return indices;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        Set<PropertyStorageSpec> specs = new LinkedHashSet<>(BASE_PROPERTIES);
        return specs;
    }

    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(SpecimenTablesTemplate template)
    {
        return template.getExtraSpecimenProperties();
    }

    // For use by Upgrade code
    public static PropertyStorageSpec getDrawDateStorageSpec()
    {
        return new PropertyStorageSpec(DRAWDATE, JdbcType.DATE, 0);
    }
    public static PropertyStorageSpec getDrawTimeStorageSpec()
    {
        return new PropertyStorageSpec(DRAWTIME, JdbcType.TIME, 0);
    }
}
