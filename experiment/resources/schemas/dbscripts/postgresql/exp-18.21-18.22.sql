
-- Issue 35817 - widen column to allow for longer paths and file names
ALTER TABLE exp.Data ALTER COLUMN DataFileURL TYPE VARCHAR(600);
