-- Detection of the data class Compound.Structure2D attachment column was corrected in a recent PR: https://github.com/LabKey/platform/pull/7513
-- This re-populates the ParentType column to pick up those changes.
SELECT core.executeJavaUpgradeCode('populateAttachmentParentTypeColumn');
