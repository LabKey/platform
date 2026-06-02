/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- This index overlaps with pk_relatedissues
DROP INDEX ix_relatedissues_issueid ON issues.RelatedIssues;
