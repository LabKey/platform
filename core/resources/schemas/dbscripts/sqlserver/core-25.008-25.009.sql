ALTER TABLE core.Documents ADD ParentType NVARCHAR(300);

EXEC core.executeJavaUpgradeCode 'populateAttachmentParentTypeColumn';
