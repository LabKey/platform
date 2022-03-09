package org.labkey.api.study.importer;

import org.labkey.api.admin.ImportException;

// Implemented by importers that import from the study node of a folder archive but are registered by other modules,
// namely specimen importers
public interface SimpleStudyImporter extends BaseStudyImporter<SimpleStudyImportContext>
{
    enum Timing {Early, Late}

    Timing getTiming();

    // Called before any importing occurs; allows for early initialization of importer state
    void preHandling(SimpleStudyImportContext ctx) throws ImportException;

    // Called when the import job is ending; allows for resetting importer state irrespective of import success or failure
    void postHandling(SimpleStudyImportContext ctx);
}
