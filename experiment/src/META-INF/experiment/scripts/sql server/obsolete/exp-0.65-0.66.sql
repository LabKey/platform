-- Rename IX index name, failing silently 
CREATE PROCEDURE RenameIndex(@owner AS VARCHAR(20), @tableName AS VARCHAR(100), @oldName AS VARCHAR(100), @newName AS VARCHAR(100)) AS
	IF (SELECT COUNT(*) FROM sysindexes WHERE NAME = @oldName) > 0
		BEGIN
			DECLARE @fullOldName VARCHAR(200)
			SET @fullOldName = @owner + '.' + @tableName + '.' + @oldName
			EXEC sp_rename @fullOldName, @newName
		END
GO


EXEC RenameIndex 'exp', 'Data', 'IDX_CL_Data_RunId', 'IX_CL_DataRunId'
EXEC RenameIndex 'exp', 'ExperimentRun', 'IDX_CL_ExperimentRun_ExperimentLSID', 'IX_CL_ExperimentRun_ExperimentLSID'
EXEC RenameIndex 'exp', 'Material', 'IDX_CL_Material_RunId', 'IX_CL_Material_RunId'
EXEC RenameIndex 'exp', 'Property', 'IDX_CL_Property_ParentURI', 'IX_CL_Property_ParentURI'
EXEC RenameIndex 'exp', 'ProtocolApplication', 'IDX_CL_ProtocolApplication_RunId', 'IX_CL_ProtocolApplication_RunId'
EXEC RenameIndex 'exp', 'MaterialInput', 'IDX_MaterialInput_TargetApplicationId', 'IX_MaterialInput_TargetApplicationId'
EXEC RenameIndex 'exp', 'DataInput', 'IDX_DataInput_TargetApplicationId', 'IX_DataInput_TargetApplicationId'
EXEC RenameIndex 'exp', 'ProtocolApplication', 'IDX_ProtocolApplication_ProtocolLSID', 'IX_ProtocolApplication_ProtocolLSID'
GO

DROP PROCEDURE RenameIndex
GO