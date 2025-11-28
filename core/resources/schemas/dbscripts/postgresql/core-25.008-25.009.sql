ALTER TABLE core.Documents ADD ParentType VARCHAR(300);

SELECT core.executeJavaUpgradeCode('populateAttachmentParentTypeColumn');
