package org.labkey.api.study.importer;

// Implemented by importers that import from a study archive but are registered by other modules,
// namely specimen importers
public interface SimpleStudyImporter extends BaseStudyImporter<SimpleStudyImportContext>
{
}
