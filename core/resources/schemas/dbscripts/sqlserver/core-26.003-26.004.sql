/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Detection of the data class Compound.Structure2D attachment column was corrected in a recent PR: https://github.com/LabKey/platform/pull/7513
-- This re-populates the ParentType column to pick up those changes.
EXEC core.executeJavaUpgradeCode 'populateAttachmentParentTypeColumn';
