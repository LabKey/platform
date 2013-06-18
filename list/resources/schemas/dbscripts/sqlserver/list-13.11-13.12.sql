ALTER TABLE exp.list DROP CONSTRAINT UQ_LIST;
GO

EXEC core.executeJavaUpgradeCode 'upgradeListDomains';

ALTER TABLE exp.list ADD CONSTRAINT UQ_LIST UNIQUE(Container, Name);

DROP TABLE exp.indexinteger;
DROP TABLE exp.indexvarchar;

ALTER TABLE exp.list DROP CONSTRAINT UQ_RowId;
ALTER TABLE exp.list DROP COLUMN rowid;
GO
