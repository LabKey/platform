/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Identical to AttachmentsGroupedByParentType, but this query's .query.xml provides an admin-console-specific Count URL
SELECT ParentType, COUNT(*) AS "Count"
FROM Attachments
GROUP BY ParentType
ORDER BY ParentType
