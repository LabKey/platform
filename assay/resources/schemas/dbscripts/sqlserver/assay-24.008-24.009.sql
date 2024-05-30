-- Add index on assay.WellGroupPositions.WellId to improve performance of DELETE operation on assay.Well table.
CREATE INDEX IX_WellGroupPositions_WellId ON assay.WellGroupPositions (WellId);