/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- PostgreSQL 18+ names NOT NULL constraints, and core-26.011-26.012.sql's rename of SourceId to Parent didn't rename this one
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'core.AttachmentPointer'::regclass AND conname = 'attachmentpointer_sourceid_not_null') THEN
        ALTER TABLE core.AttachmentPointer RENAME CONSTRAINT attachmentpointer_sourceid_not_null TO attachmentpointer_parent_not_null;
    END IF;
END $$;
