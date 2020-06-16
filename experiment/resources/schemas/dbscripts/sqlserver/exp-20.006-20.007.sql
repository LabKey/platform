
-- Rebuilding Container index with more columns
EXEC core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_Container';
GO

CREATE INDEX IX_Data_Container_ClassId_LSID_Name_Modified_RowId ON exp.Data (Container, classId, LSID, Name,  Modified, RowId);