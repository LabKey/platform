-- Shift the core.Logins PK from Email to UserId. Add UserId column as NULLABLE, populate it from core.Principals,
-- delete rows that didn't join (RowId IS NULL), make RowId NOT NULL, drop the old PK, and add the new PK.

ALTER TABLE core.Logins ADD UserId USERID;
GO

UPDATE core.Logins SET UserId = (SELECT UserId FROM core.Principals p WHERE Name = Email);
DELETE FROM core.Logins WHERE UserId IS NULL;
ALTER TABLE core.Logins ALTER COLUMN UserId USERID NOT NULL;
GO

ALTER TABLE core.Logins DROP CONSTRAINT PK_Logins;
ALTER TABLE core.Logins ADD CONSTRAINT PK_Logins PRIMARY KEY (UserId);
