/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- "publish" is the only supported type now, so migrate any "ancillary" studies to "publish"
UPDATE study.StudySnapshot SET Type = 'publish';
