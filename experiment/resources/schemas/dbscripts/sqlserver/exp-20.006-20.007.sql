
-- Rebuilding Container index with more columns
EXEC core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_Container';
GO

EXEC core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId';
GO

CREATE INDEX IX_Data_Container_LSID_Name_ClassId_Modified_RowId ON exp.Data (Container, LSID, Name, classId, Modified, RowId);
CREATE INDEX IX_Data_SourceApplicationId_RowId ON exp.Data (SourceApplicationId, RowId);