
-- Rebuilding Container index with more columns
EXEC core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_Container';
GO

CREATE INDEX IX_Data_Container_LSID_Name_ClassId_Modified ON exp.Data (Container, LSID, Name, classId, Modified);
CREATE INDEX IX_Data_ClassId_Container_RowId_LSID_Modified ON exp.Data (Container, LSID, Name, classId, Modified);