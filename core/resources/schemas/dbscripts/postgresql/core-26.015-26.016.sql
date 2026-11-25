/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- 26.004 ran before FileSystem matched container parents, so 26.015 marked default file root attachments 'Unknown'
SELECT core.executeJavaUpgradeCode('populateAttachmentParentTypeColumn');
