/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Core scripts run before labbook's, so core takes ownership of labbook.AttachmentPointer here. labbook-26.000-26.001.sql
-- drops the copy that labbook's bootstrap script still creates.
DO $$
DECLARE
    globalParent ENTITYID := (SELECT p.Value FROM prop.Properties p JOIN prop.PropertySets s ON s.Set = p.Set WHERE s.Category = 'ELN' AND p.Name = 'attachmentParentId');
BEGIN
    IF to_regclass('labbook.AttachmentPointer') IS NOT NULL THEN
        ALTER TABLE labbook.AttachmentPointer SET SCHEMA core;
        ALTER TABLE core.AttachmentPointer RENAME COLUMN SourceId TO Parent;
        ALTER TABLE core.AttachmentPointer RENAME CONSTRAINT UQ_AttachmentPointer_Name_SourceId TO UQ_AttachmentPointer_Name_Parent;
        ALTER TABLE core.AttachmentPointer ADD ParentType VARCHAR(300);
        ALTER TABLE core.AttachmentPointer ADD Container ENTITYID;
        ALTER TABLE core.AttachmentPointer ADD AttachmentId INT;

        IF globalParent IS NULL AND EXISTS (SELECT 1 FROM core.AttachmentPointer) THEN
            RAISE EXCEPTION 'core.AttachmentPointer has rows but the ELN global attachment parent property is missing';
        END IF;

        UPDATE core.AttachmentPointer ap SET Container = s.Container
        FROM (
            SELECT EntityId AS SourceId, Container FROM labbook.Notebook
            UNION ALL SELECT EntityId, Container FROM labbook.NotebookEntry
            UNION ALL SELECT ContentEntityId, Container FROM labbook.NotebookEntry
            UNION ALL SELECT h.EntityId, n.Container FROM labbook.NotebookHistory h JOIN labbook.Notebook n ON n.RowId = h.NotebookId
            UNION ALL SELECT h.EntityId, n.Container FROM labbook.NotebookEntryHistory h JOIN labbook.Notebook n ON n.RowId = h.NotebookId
            UNION ALL SELECT h.ContentEntityId, n.Container FROM labbook.NotebookEntryHistory h JOIN labbook.Notebook n ON n.RowId = h.NotebookId
        ) s
        WHERE ap.Parent = s.SourceId;

        -- Documents are keyed by bare hash, so content stored under different extensions (.png, .PNG) collapses to one document
        UPDATE core.AttachmentPointer ap SET AttachmentId = d.RowId
        FROM (SELECT LEFT(DocumentName, 64) AS Hash, MIN(RowId) AS RowId FROM core.Documents WHERE Parent = globalParent GROUP BY LEFT(DocumentName, 64)) d
        WHERE LEFT(ap.Hash, 64) = d.Hash;

        DELETE FROM core.AttachmentPointer WHERE Container IS NULL OR AttachmentId IS NULL;
        DELETE FROM core.Documents d WHERE d.Parent = globalParent AND NOT EXISTS (SELECT 1 FROM core.AttachmentPointer ap WHERE ap.AttachmentId = d.RowId);
        UPDATE core.Documents SET DocumentName = LEFT(DocumentName, 64) WHERE Parent = globalParent;

        ALTER TABLE core.AttachmentPointer DROP COLUMN Hash;
        ALTER TABLE core.AttachmentPointer ALTER COLUMN Container SET NOT NULL;
        ALTER TABLE core.AttachmentPointer ALTER COLUMN AttachmentId SET NOT NULL;
    ELSE
        CREATE TABLE core.AttachmentPointer
        (
            RowId SERIAL NOT NULL,
            Name VARCHAR(195) NOT NULL,
            Parent ENTITYID NOT NULL,
            Created TIMESTAMP NOT NULL,
            CreatedBy USERID NOT NULL,
            ParentType VARCHAR(300),
            Container ENTITYID NOT NULL,
            AttachmentId INT NOT NULL,

            CONSTRAINT PK_AttachmentPointer PRIMARY KEY (RowId),
            CONSTRAINT UQ_AttachmentPointer_Name_Parent UNIQUE (Parent, Name)
        );
    END IF;
END $$;

ALTER TABLE core.AttachmentPointer ADD CONSTRAINT FK_AttachmentPointer_AttachmentId FOREIGN KEY (AttachmentId) REFERENCES core.Documents (RowId);
CREATE INDEX IX_AttachmentPointer_AttachmentId ON core.AttachmentPointer (AttachmentId);
CREATE INDEX IX_AttachmentPointer_Container ON core.AttachmentPointer (Container);
