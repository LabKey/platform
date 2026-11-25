/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- labbook's tables don't exist yet when core bootstraps. Values must match the getUniqueName() of labbook's AttachmentParentType implementations.
DO $$
BEGIN
    IF to_regclass('labbook.Notebook') IS NOT NULL THEN
        UPDATE core.AttachmentPointer ap SET ParentType = s.ParentType
        FROM (
            SELECT EntityId AS Parent, 'Notebook' AS ParentType FROM labbook.Notebook
            UNION ALL SELECT EntityId, 'NotebookEntry' FROM labbook.NotebookEntry
            UNION ALL SELECT ContentEntityId, 'NotebookEntryContent' FROM labbook.NotebookEntry
            UNION ALL SELECT EntityId, 'NotebookVersion' FROM labbook.NotebookHistory
            UNION ALL SELECT EntityId, 'NotebookVersion' FROM labbook.NotebookEntryHistory
            UNION ALL SELECT ContentEntityId, 'NotebookVersion' FROM labbook.NotebookEntryHistory
        ) s
        WHERE ap.Parent = s.Parent AND ap.ParentType IS NULL;
    END IF;
END $$;

ALTER TABLE core.AttachmentPointer ALTER COLUMN ParentType SET NOT NULL;
