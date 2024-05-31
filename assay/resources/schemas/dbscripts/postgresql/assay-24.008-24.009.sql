-- Add index on assay.Well.SampleId to improve performance of DELETE operation on exp.Material table.
CREATE INDEX IX_Well_SampleId ON assay.Well (SampleId);

-- Add index on assay.WellGroupPositions.WellId to improve performance of DELETE operation on assay.Well table.
CREATE INDEX IX_WellGroupPositions_WellId ON assay.WellGroupPositions (WellId);