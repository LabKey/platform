/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- This is utilized by the PlateEditableGrid to support querying for samples and having them be constrained
-- to a plate set's "PrimaryPlateSet".
SELECT
    DISTINCT W.SampleId.RowId,
             W.SampleId.Name,
             W.PlateId.PlateSet AS PlateSetRowId
FROM
    plate.well AS W
WHERE
    W.sampleId IS NOT NULL
