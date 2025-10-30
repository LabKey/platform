-- These tables have FKs to exp.Data without corresponding indices. Add indices to speed up exp.Data delete.
CREATE INDEX IX_DataInput_DataId ON exp.DataInput (DataId);
CREATE INDEX IX_DataAncestors_RowId ON exp.DataAncestors (RowId);
