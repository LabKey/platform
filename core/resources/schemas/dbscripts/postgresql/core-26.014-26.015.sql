/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Every attachment is now reached through core.AttachmentPointers; core.Attachments holds only content.
ALTER TABLE core.AttachmentPointer RENAME TO AttachmentPointers;
ALTER TABLE core.AttachmentPointers RENAME CONSTRAINT PK_AttachmentPointer TO PK_AttachmentPointers;
ALTER TABLE core.AttachmentPointers RENAME CONSTRAINT UQ_AttachmentPointer_Name_Parent TO UQ_AttachmentPointers_Name_Parent;
ALTER TABLE core.AttachmentPointers RENAME CONSTRAINT FK_AttachmentPointer_AttachmentId TO FK_AttachmentPointers_AttachmentId;
ALTER INDEX core.IX_AttachmentPointer_AttachmentId RENAME TO IX_AttachmentPointers_AttachmentId;
ALTER INDEX core.IX_AttachmentPointer_Container RENAME TO IX_AttachmentPointers_Container;
ALTER SEQUENCE core.AttachmentPointer_RowId_Seq RENAME TO AttachmentPointers_RowId_Seq;

-- PostgreSQL 18+ names NOT NULL constraints after the table; older versions have none to rename
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint WHERE conrelid = 'core.AttachmentPointers'::regclass AND contype = 'n' AND conname LIKE 'attachmentpointer\_%' LOOP
        EXECUTE format('ALTER TABLE core.AttachmentPointers RENAME CONSTRAINT %I TO %I', c.conname, 'attachmentpointers' || substr(c.conname, length('attachmentpointer') + 1));
    END LOOP;
END $$;

ALTER TABLE core.Documents RENAME TO Attachments;
ALTER TABLE core.Attachments RENAME CONSTRAINT PK_Documents TO PK_Attachments;
ALTER SEQUENCE core.Documents_RowId_Seq RENAME TO Attachments_RowId_Seq;
ALTER TABLE core.Attachments RENAME COLUMN DocumentSize TO Size;
ALTER TABLE core.Attachments RENAME COLUMN DocumentType TO Type;

-- Nullable until deduplication hashes every database attachment; PostgreSQL allows many NULLs in a unique column
ALTER TABLE core.Attachments ADD Hash VARCHAR(64);
ALTER TABLE core.Attachments ADD CONSTRAINT UQ_Attachments_Hash UNIQUE (Hash);

ALTER TABLE core.AttachmentPointers ADD LastIndexed TIMESTAMP NULL;

DO $$
DECLARE
    globalParent ENTITYID;
BEGIN
    -- prop doesn't exist yet when core bootstraps
    IF to_regclass('prop.Properties') IS NOT NULL THEN
        globalParent := (SELECT p.Value FROM prop.Properties p JOIN prop.PropertySets s ON s.Set = p.Set
            WHERE s.ObjectId = (SELECT EntityId FROM core.Containers WHERE Parent IS NULL) AND s.UserId = 0 AND s.Category = 'ELN' AND p.Name = 'attachmentParentId');

        DELETE FROM prop.Properties p USING prop.PropertySets s
            WHERE s.Set = p.Set AND s.ObjectId = (SELECT EntityId FROM core.Containers WHERE Parent IS NULL) AND s.UserId = 0 AND s.Category = 'ELN' AND p.Name = 'attachmentParentId';

        -- Left by the deleted AttachmentHashing maintenance task
        DELETE FROM prop.Properties p USING prop.PropertySets s
            WHERE s.Set = p.Set AND s.Category = 'AttachmentHashingStatistics';
        DELETE FROM prop.PropertySets WHERE Category = 'AttachmentHashingStatistics';
    END IF;

    -- ELN global attachments already have pointers and are named by bare hash
    UPDATE core.Attachments SET Hash = DocumentName WHERE Parent = globalParent;

    -- A row with no name can't be looked up, so it gets no pointer and the cleanup task deletes it
    INSERT INTO core.AttachmentPointers (Name, Parent, ParentType, Container, AttachmentId, Created, CreatedBy, LastIndexed)
        SELECT DocumentName, Parent, COALESCE(ParentType, 'Unknown'), Container, RowId, COALESCE(Created, Modified, CURRENT_TIMESTAMP), COALESCE(CreatedBy, ModifiedBy, 0), LastIndexed
        FROM core.Attachments
        WHERE Parent IS DISTINCT FROM globalParent AND DocumentName IS NOT NULL;
END $$;

-- File-system attachments have no content to hash, so each gets a unique placeholder
UPDATE core.Attachments SET Hash = gen_random_uuid()::VARCHAR WHERE Document IS NULL;

-- Also drops UQ_Documents_Parent_DocumentName and IX_Documents_Container
ALTER TABLE core.Attachments
    DROP COLUMN DocumentName,
    DROP COLUMN Parent,
    DROP COLUMN Container,
    DROP COLUMN ParentType,
    DROP COLUMN Created,
    DROP COLUMN CreatedBy,
    DROP COLUMN LastIndexed,
    DROP COLUMN _ts,
    DROP COLUMN Owner,
    DROP COLUMN Modified,
    DROP COLUMN ModifiedBy;
