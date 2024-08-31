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
