/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- 26.015 renamed core.Documents but not the NOT NULL constraints PostgreSQL 18+ named after it
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint WHERE conrelid = 'core.Attachments'::regclass AND contype = 'n' AND conname LIKE 'documents\_%' LOOP
        EXECUTE format('ALTER TABLE core.Attachments RENAME CONSTRAINT %I TO %I', c.conname, 'attachments' || substr(c.conname, length('documents') + 1));
    END LOOP;
END $$;
