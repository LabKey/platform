ALTER TABLE exp.list DROP CONSTRAINT UQ_LIST;

SELECT core.executeJavaUpgradeCode('upgradeListDomains');

ALTER TABLE exp.list ADD CONSTRAINT UQ_LIST UNIQUE(Container, Name);

DROP TABLE exp.indexinteger, exp.indexvarchar;

ALTER TABLE exp.list DROP CONSTRAINT uq_rowid;
ALTER TABLE exp.list DROP COLUMN rowid;

