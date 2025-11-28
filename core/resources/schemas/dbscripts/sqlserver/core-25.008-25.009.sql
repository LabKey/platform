ALTER TABLE core.Documents ADD ParentType VARCHAR(300);

EXEC core.executeJavaUpgradeCode 'populateAttachmentParentTypeColumn';
