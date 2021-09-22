EXEC sp_rename 'core.qcstate', 'DataStates'
GO

ALTER TABLE core.DataStates ADD StateType NVARCHAR(20);
