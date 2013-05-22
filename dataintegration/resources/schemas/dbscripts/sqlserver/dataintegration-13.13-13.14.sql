EXEC sp_rename
    @objname = 'dataintegration.TransformRun.RowId',
    @newname = 'TransformRunID',
    @objtype = 'COLUMN';
GO
