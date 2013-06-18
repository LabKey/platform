SELECT core.executeJavaUpgradeCode('upgradeListDomains');

DROP TABLE exp.indexinteger, exp.indexvarchar;

ALTER TABLE exp.list DROP CONSTRAINT uq_rowid;
ALTER TABLE exp.list DROP COLUMN rowid;

