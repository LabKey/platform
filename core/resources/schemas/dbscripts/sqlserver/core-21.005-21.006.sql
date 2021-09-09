EXEC sp_rename 'core.qcstate', 'dataStates'
GO

ALTER TABLE core.dataStates ADD stateType NVARCHAR(20);
