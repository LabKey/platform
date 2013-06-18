EXEC core.executeJavaUpgradeCode 'upgradeListDomains';

DROP TABLE exp.indexinteger;
DROP TABLE exp.indexvarchar;

ALTER TABLE exp.list DROP CONSTRAINT UQ_RowId;
ALTER TABLE exp.list DROP COLUMN rowid;
GO
