package org.labkey.specimen.importer;

import org.labkey.api.specimen.SpecimenSchema;

// Extracted from SpecimenImporter to ease the specimen module migration
public class ImportTypes
{
    private ImportTypes()
    {
    }

    public static final String DATETIME_TYPE = "SpecimenImporter/DateTime";
    public static final String DURATION_TYPE = "SpecimenImporter/TimeOnlyDate";
    public static final String NUMERIC_TYPE = "NUMERIC(15,4)";
    public static final String BOOLEAN_TYPE = SpecimenSchema.get().getSqlDialect().getBooleanDataType();
    public static final String BINARY_TYPE = SpecimenSchema.get().getSqlDialect().getBinaryDataType();
}
