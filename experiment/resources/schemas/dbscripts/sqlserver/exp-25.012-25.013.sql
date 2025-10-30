-- Not needed since it's redundant with exp.DataInput's PK
DROP INDEX IX_DataInput_DataId ON exp.DataInput;

CREATE INDEX IX_MaterialAncestors_RowId ON exp.MaterialAncestors (RowId);
