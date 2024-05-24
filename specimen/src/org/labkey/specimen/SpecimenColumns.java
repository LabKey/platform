package org.labkey.specimen;

import org.labkey.specimen.importer.ImportTypes;
import org.labkey.specimen.importer.ImportableColumn;
import org.labkey.specimen.importer.SpecimenColumn;
import org.labkey.specimen.importer.TargetTable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

// Column definitions that are shared between specimen writers and importers
public class SpecimenColumns
{
    public static final String GLOBAL_UNIQUE_ID_TSV_COL = "global_unique_specimen_id";

    public static final String LAB_ID_TSV_COL = "lab_id";
    public static final String SPEC_NUMBER_TSV_COL = "specimen_number";
    public static final String EVENT_ID_COL = "record_id";
    public static final String VISIT_COL = "visit_value";

    // SpecimenEvent columns that form a psuedo-unique constraint
    public static final SpecimenColumn GLOBAL_UNIQUE_ID, LAB_ID, SHIP_DATE, STORAGE_DATE, LAB_RECEIPT_DATE, DRAW_TIMESTAMP;
    public static final SpecimenColumn VISIT_VALUE;

    public static final List<SpecimenColumn> BASE_SPECIMEN_COLUMNS = List.of(
        new SpecimenColumn(EVENT_ID_COL, "ExternalId", "BIGINT NOT NULL", TargetTable.SPECIMEN_EVENTS, true),
        new SpecimenColumn("record_source", "RecordSource", "VARCHAR(20)", TargetTable.SPECIMEN_EVENTS),
        GLOBAL_UNIQUE_ID = new SpecimenColumn(GLOBAL_UNIQUE_ID_TSV_COL, "GlobalUniqueId", "VARCHAR(50)", true, TargetTable.VIALS, true),
        LAB_ID = new SpecimenColumn(LAB_ID_TSV_COL, "LabId", "INT", TargetTable.SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER") {
            @Override
            public boolean isUnique() { return true; }
        },
        new SpecimenColumn("originating_location", "OriginatingLocationId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "Site", "ExternalId", "LEFT OUTER"),
        new SpecimenColumn("unique_specimen_id", "UniqueSpecimenId", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("ptid", "Ptid", "VARCHAR(32)", true, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("parent_specimen_id", "ParentSpecimenId", "INT", TargetTable.SPECIMEN_EVENTS),
        DRAW_TIMESTAMP = new SpecimenColumn("draw_timestamp", "DrawTimestamp", ImportTypes.DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("sal_receipt_date", "SalReceiptDate", ImportTypes.DATETIME_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn(SPEC_NUMBER_TSV_COL, "SpecimenNumber", "VARCHAR(50)", true, TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("class_id", "ClassId", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        VISIT_VALUE = new SpecimenColumn(VISIT_COL, "VisitValue", ImportTypes.NUMERIC_TYPE, TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("protocol_number", "ProtocolNumber", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("visit_description", "VisitDescription", "VARCHAR(10)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("other_specimen_id", "OtherSpecimenId", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("volume", "Volume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS, "MAX"),
        new SpecimenColumn("volume_units", "VolumeUnits", "VARCHAR(20)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("stored", "Stored", "INT", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("storage_flag", "storageFlag", "INT", TargetTable.SPECIMEN_EVENTS),
        STORAGE_DATE = new SpecimenColumn("storage_date", "StorageDate", ImportTypes.DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
        new SpecimenColumn("ship_flag", "ShipFlag", "INT", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("ship_batch_number", "ShipBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
        SHIP_DATE = new SpecimenColumn("ship_date", "ShipDate", ImportTypes.DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
        new SpecimenColumn("imported_batch_number", "ImportedBatchNumber", "INT", TargetTable.SPECIMEN_EVENTS),
        LAB_RECEIPT_DATE = new SpecimenColumn("lab_receipt_date", "LabReceiptDate", ImportTypes.DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS, true),
        new SpecimenColumn("expected_time_value", "ExpectedTimeValue", "FLOAT", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("expected_time_unit", "ExpectedTimeUnit", "VARCHAR(15)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("group_protocol", "GroupProtocol", "INT", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("sub_additive_derivative", "SubAdditiveDerivative", "VARCHAR(50)", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("comments", "Comments", "VARCHAR(500)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("primary_specimen_type_id", "PrimaryTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenPrimaryType", "ExternalId", "LEFT OUTER"),
        new SpecimenColumn("derivative_type_id", "DerivativeTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
        new SpecimenColumn("derivative_type_id_2", "DerivativeTypeId2", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenDerivative", "ExternalId", "LEFT OUTER"),
        new SpecimenColumn("additive_type_id", "AdditiveTypeId", "INT", TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS, "SpecimenAdditive", "ExternalId", "LEFT OUTER"),
        new SpecimenColumn("specimen_condition", "SpecimenCondition", "VARCHAR(30)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("sample_number", "SampleNumber", "INT", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("x_sample_origin", "XSampleOrigin", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("external_location", "ExternalLocation", "VARCHAR(50)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("update_timestamp", "UpdateTimestamp", ImportTypes.DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("requestable", "Requestable", ImportTypes.BOOLEAN_TYPE, TargetTable.VIALS),
        new SpecimenColumn("shipped_from_lab", "ShippedFromLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("shipped_to_lab", "ShippedtoLab", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("frozen_time", "FrozenTime", ImportTypes.DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("primary_volume", "PrimaryVolume", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("primary_volume_units", "PrimaryVolumeUnits", "VARCHAR(20)", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("processed_by_initials", "ProcessedByInitials", "VARCHAR(32)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("processing_date", "ProcessingDate", ImportTypes.DATETIME_TYPE, TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("processing_time", "ProcessingTime", ImportTypes.DURATION_TYPE, TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("quality_comments", "QualityComments", "VARCHAR(500)", TargetTable.SPECIMEN_EVENTS),
        new SpecimenColumn("total_cell_count", "TotalCellCount", "FLOAT", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("tube_type", "TubeType", "VARCHAR(64)", TargetTable.VIALS_AND_SPECIMEN_EVENTS),
        new SpecimenColumn("input_hash", "InputHash", ImportTypes.BINARY_TYPE, TargetTable.SPECIMEN_EVENTS)   // Not pulled from file... maybe this should be a ComputedColumn
    );

    public static final Collection<ImportableColumn> ADDITIVE_COLUMNS = List.of(
        new ImportableColumn("additive_id", "ExternalId", "INT NOT NULL", true),
        new ImportableColumn("ldms_additive_code", "LdmsAdditiveCode", "VARCHAR(30)"),
        new ImportableColumn("labware_additive_code", "LabwareAdditiveCode", "VARCHAR(20)"),
        new ImportableColumn("additive", "Additive", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> DERIVATIVE_COLUMNS = Arrays.asList(
        new ImportableColumn("derivative_id", "ExternalId", "INT NOT NULL", true),
        new ImportableColumn("ldms_derivative_code", "LdmsDerivativeCode", "VARCHAR(30)"),
        new ImportableColumn("labware_derivative_code", "LabwareDerivativeCode", "VARCHAR(20)"),
        new ImportableColumn("derivative", "Derivative", "VARCHAR(100)")
    );

    public static final Collection<ImportableColumn> SITE_COLUMNS = List.of(
        new ImportableColumn("lab_id", "ExternalId", "INT NOT NULL", true),
        new ImportableColumn("ldms_lab_code", "LdmsLabCode", "INT"),
        new ImportableColumn("labware_lab_code", "LabwareLabCode", "VARCHAR(20)", false, true),
        new ImportableColumn("lab_name", "Label", "VARCHAR(200)", false, true),
        new ImportableColumn("lab_upload_code", "LabUploadCode", "VARCHAR(10)"),
        new ImportableColumn("is_sal", "Sal", ImportTypes.BOOLEAN_TYPE, Boolean.FALSE),
        new ImportableColumn("is_repository", "Repository", ImportTypes.BOOLEAN_TYPE, Boolean.FALSE),
        new ImportableColumn("is_clinic", "Clinic", ImportTypes.BOOLEAN_TYPE, Boolean.FALSE),
        new ImportableColumn("is_endpoint", "Endpoint", ImportTypes.BOOLEAN_TYPE, Boolean.FALSE),
        new ImportableColumn("street_address", "StreetAddress", "VARCHAR(200)", false, true),
        new ImportableColumn("city", "City", "VARCHAR(200)", false, true),
        new ImportableColumn("governing_district", "GoverningDistrict", "VARCHAR(200)", false, true),
        new ImportableColumn("country", "Country", "VARCHAR(200)", false, true),
        new ImportableColumn("postal_area", "PostalArea", "VARCHAR(50)", false, true),
        new ImportableColumn("description", "Description", "VARCHAR(500)", false, true)
    );

    public static final Collection<ImportableColumn> PRIMARYTYPE_COLUMNS = List.of(
        new ImportableColumn("primary_type_id", "ExternalId", "INT NOT NULL", true),
        new ImportableColumn("primary_type_ldms_code", "PrimaryTypeLdmsCode", "VARCHAR(5)"),
        new ImportableColumn("primary_type_labware_code", "PrimaryTypeLabwareCode", "VARCHAR(5)"),
        new ImportableColumn("primary_type", "PrimaryType", "VARCHAR(100)")
    );
}
