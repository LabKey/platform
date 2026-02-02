-- Extraction of parent EntityIds from data class LSIDs has been fixed, so re-run population of the ParentType column.
-- Keep the invocation in core-25.008-25.009.sql since core-25.009-25.010.sql relies on ParentType being populated.
EXEC core.executeJavaUpgradeCode 'populateAttachmentParentTypeColumn';
