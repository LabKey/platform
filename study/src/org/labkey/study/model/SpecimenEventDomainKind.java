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
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SpecimenEventDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "SpecimenEvent";
    private static final String NAMESPACE_PREFIX = "SpecimenEvent";

    private static final String ROWID = "RowId";
    private static final String VIALID = "VialId";
    private static final String LABID = "LabId";
    private static final String UNIQUESPECIMENID = "UniqueSpecimenId";
    private static final String PARENTSPECIMENID = "ParentSpecimenId";
    private static final String STORED = "Stored";
    private static final String STORAGEFLAG = "StorageFlag";
    private static final String STORAGEDATE = "StorageDate";
    private static final String SHIPFLAG = "ShipFlag";
    private static final String SHIPBATCHNUMBER = "ShipBatchNumber";
    private static final String SHIPDATE = "ShipDate";
    private static final String IMPORTEDBATCHNUMBER = "ImportedBatchNumber";
    private static final String LABRECEIPTDATE = "LabReceiptDate";
    private static final String COMMENTS = "Comments";
    private static final String SPECIMENCONDITION = "SpecimenCondition";
    private static final String SAMPLENUMBER = "SampleNumber";
    private static final String XSAMPLEORIGIN = "XsampleOrigin";
    private static final String EXTERNALLOCATION = "ExternalLocation";
    private static final String UPDATETIMESTAMP = "UpdateTimestamp";
    private static final String OTHERSPECIMENID = "OtherSpecimenId";
    private static final String EXPECTEDTIMEUNIT = "ExpectedTimeUnit";
    private static final String EXPECTEDTIMEVALUE = "ExpectedTimeValue";
    private static final String GROUPPROTOCOL = "GroupProtocol";
    private static final String RECORDSOURCE = "RecordSource";
    private static final String SPECIMENNUMBER = "SpecimenNumber";
    private static final String EXTERNALID = "ExternalId";
    private static final String SHIPPEDFROMLAB = "ShippedFromLab";
    private static final String SHIPPEDTOLAB = "ShippedToLab";
    private static final String PARTICIPANTID = "PTID";
    private static final String DRAWTIMESTAMP = "DrawTimestamp";
    private static final String SALRECEIPTDATE = "SalReceiptDate";
    private static final String CLASSID = "ClassId";
    private static final String VISITVALUE = "VisitValue";
    private static final String PROTOCOLNUMBER = "ProtocolNumber";
    private static final String VISITDESCRIPTION = "VisitDescription";
    private static final String VOLUME = "Volume";
    private static final String VOLUMEUNITS = "VolumeUnits";
    private static final String SUBADDITIVEDERIVATIVE = "SubadditiveDerivative";
    private static final String PRIMARYTYPEID = "PrimaryTypeId";
    private static final String ADDITIVETYPEID = "AdditiveTypeId";
    private static final String DERIVATIVETYPEID = "DerivativeTypeId";
    private static final String DERIVATIVETYPEID2 = "DerivativeTypeId2";
    private static final String ORIGINATINGLOCATIONID = "OriginatingLocationId";
    private static final String FROZENTIME = "FrozenTime";
    private static final String PROCESSINGTIME = "ProcessingTime";
    private static final String PRIMARYVOLUME = "PrimaryVolume";
    private static final String PRIMARYVOLUMEUNITS = "PrimaryVolumeUnits";
    private static final String PROCESSEDBYINITIALS = "ProcessedByInitials";
    private static final String PROCESSINGDATE = "ProcessingDate";
    private static final String TOTALCELLCOUNT = "TotalCellCount";
    private static final String QUALITYCOMMENTS = "QualityComments";
    private static final String INPUTHASH = "InputHash";
    private static final String OBSOLETE = "Obsolete";
    private static final String TUBETYPE = "TubeType";

    private static final List<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> BASE_INDICES;
    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(ROWID, JdbcType.BIGINT, 0, PropertyStorageSpec.Special.PrimaryKey, false, true, null),
            new PropertyStorageSpec(VIALID,  JdbcType.BIGINT, 0, false, null),
            new PropertyStorageSpec(LABID,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(UNIQUESPECIMENID,  JdbcType.VARCHAR, 50),
            new PropertyStorageSpec(PARENTSPECIMENID,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(STORED,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(STORAGEFLAG,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(STORAGEDATE,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(SHIPFLAG,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(SHIPBATCHNUMBER,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(SHIPDATE,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(IMPORTEDBATCHNUMBER,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(LABRECEIPTDATE,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(COMMENTS,  JdbcType.VARCHAR, 500),
            new PropertyStorageSpec(SPECIMENCONDITION,  JdbcType.VARCHAR, 30),
            new PropertyStorageSpec(SAMPLENUMBER,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(XSAMPLEORIGIN,  JdbcType.VARCHAR, 50),
            new PropertyStorageSpec(EXTERNALLOCATION,  JdbcType.VARCHAR, 50),
            new PropertyStorageSpec(UPDATETIMESTAMP,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(OTHERSPECIMENID,  JdbcType.VARCHAR, 50),
            new PropertyStorageSpec(EXPECTEDTIMEUNIT,  JdbcType.VARCHAR, 15),
            new PropertyStorageSpec(EXPECTEDTIMEVALUE,  JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(GROUPPROTOCOL,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(RECORDSOURCE,  JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(SPECIMENNUMBER,  JdbcType.VARCHAR, 50),
            new PropertyStorageSpec(EXTERNALID,  JdbcType.BIGINT, 0),
            new PropertyStorageSpec(SHIPPEDFROMLAB,  JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(SHIPPEDTOLAB,  JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(PARTICIPANTID,  JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(DRAWTIMESTAMP,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(SALRECEIPTDATE,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(CLASSID,  JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(VISITVALUE,  JdbcType.DECIMAL, 0),
            new PropertyStorageSpec(PROTOCOLNUMBER,  JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(VISITDESCRIPTION,  JdbcType.VARCHAR, 10),
            new PropertyStorageSpec(VOLUME,  JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(VOLUMEUNITS,  JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(SUBADDITIVEDERIVATIVE,  JdbcType.VARCHAR, 50),
            new PropertyStorageSpec(PRIMARYTYPEID,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(ADDITIVETYPEID,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(DERIVATIVETYPEID,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(DERIVATIVETYPEID2,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(ORIGINATINGLOCATIONID,  JdbcType.INTEGER, 0),
            new PropertyStorageSpec(FROZENTIME,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(PROCESSINGTIME,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(PRIMARYVOLUME,  JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(PRIMARYVOLUMEUNITS,  JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(PROCESSEDBYINITIALS,  JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(PROCESSINGDATE,  JdbcType.TIMESTAMP, 0),
            new PropertyStorageSpec(TOTALCELLCOUNT,  JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(QUALITYCOMMENTS,  JdbcType.VARCHAR, 500),
            new PropertyStorageSpec(INPUTHASH,  JdbcType.BINARY, 16),
            new PropertyStorageSpec(OBSOLETE,  JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(TUBETYPE, JdbcType.VARCHAR, 64, "The type of vial.")
        };
        BASE_PROPERTIES = Arrays.asList(props);

        PropertyStorageSpec.Index[] indices =
        {
            new PropertyStorageSpec.Index(false, LABID),
            new PropertyStorageSpec.Index(false, VIALID)
        };
        BASE_INDICES = new HashSet<>(Arrays.asList(indices));
    }

    final private String _vialDomainURI;

    public SpecimenEventDomainKind()
    {
        this(null);
    }

    public SpecimenEventDomainKind(String vialDomainURI)
    {
        super();
        _vialDomainURI = vialDomainURI;
    }

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
        foreignKeys.add(new PropertyStorageSpec.ForeignKey(LABID, "study", "Site", "RowId", null, true));
        foreignKeys.add(new PropertyStorageSpec.ForeignKey(VIALID, "study", "Vial", "RowId", _vialDomainURI, true));
        setForeignKeyTableInfos(container, foreignKeys, provider);
        return foreignKeys;
    }

    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(@Nullable SpecimenTablesTemplate template)
    {
        return null != template ? template.getExtraSpecimenEventProperties() : Collections.emptySet();
    }
}
