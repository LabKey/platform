package org.labkey.specimen.writer;

import org.labkey.api.specimen.SpecimenMigrationService;

public class SpecimenArchiveDataTypes
{
    public static final String SCHEMA_FILENAME = "specimens_metadata.xml";
    public static final String SPECIMEN_SETTINGS =  "Specimen Settings";
    public static final String SPECIMENS = SpecimenMigrationService.SPECIMENS_ARCHIVE_TYPE;

    private SpecimenArchiveDataTypes()
    {
    }
}
