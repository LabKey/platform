-- Not needed since it's redundant with exp.DataInput's PK
DROP INDEX exp.IX_DataInput_DataId;

CREATE INDEX IX_MaterialAncestors_RowId ON exp.MaterialAncestors (RowId);
