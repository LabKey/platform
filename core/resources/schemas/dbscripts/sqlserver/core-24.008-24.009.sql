-- LabKey no longer reads or writes to the Email column. But we'll leave the column in place until 24.12 as a precaution.
ALTER TABLE core.Logins ALTER COLUMN Email VARCHAR(255) NULL;
GO
