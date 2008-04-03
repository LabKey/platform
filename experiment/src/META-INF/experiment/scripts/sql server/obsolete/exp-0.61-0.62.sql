if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[OutputMaterialForNode]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.OutputMaterialForNode
GO
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[OutputDataForNode]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.OutputDataForNode
GO
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ChildMaterialForApplication]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ChildMaterialForApplication
GO
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[ChildDataForApplication]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.ChildDataForApplication
GO
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[MarkedOutputMaterialForRun]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.MarkedOutputMaterialForRun
GO
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[MarkedOutputDataForRun]') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.MarkedOutputDataForRun
GO



CREATE VIEW exp.ChildMaterialForApplication
AS
SELECT     exp.Material.RowId, exp.Material.LSID, exp.Material.Name, exp.Material.SourceApplicationId, exp.Material.SourceProtocolLSID, exp.Material.RunId, exp.Material.Created, 
                      exp.ProtocolApplication.RowId AS ApplicationID, exp.ProtocolApplication.LSID AS ApplicationLSID, exp.ProtocolApplication.Name AS ApplicationName, 
                      exp.ProtocolApplication.CpasType AS ApplicationType
FROM         exp.Material INNER JOIN
                      exp.ProtocolApplication ON exp.Material.SourceApplicationId = exp.ProtocolApplication.RowId
WHERE     (exp.ProtocolApplication.CpasType <> N'ExperimentRunOutput')

GO

CREATE VIEW exp.ChildDataForApplication
AS
SELECT     exp.Data.RowId, exp.Data.LSID, exp.Data.Name, exp.Data.SourceApplicationId, exp.Data.SourceProtocolLSID, exp.Data.DataFileUrl, exp.Data.RunId, exp.Data.Created, 
                      exp.ProtocolApplication.RowId AS ApplicationID, exp.ProtocolApplication.LSID AS ApplicationLSID, exp.ProtocolApplication.Name AS ApplicationName, 
                      exp.ProtocolApplication.CpasType AS ApplicationType
FROM         exp.Data INNER JOIN
                      exp.ProtocolApplication ON exp.Data.SourceApplicationId = exp.ProtocolApplication.RowId
WHERE     (exp.ProtocolApplication.CpasType <> N'ExperimentRunOutput')

GO

CREATE VIEW exp.MarkedOutputMaterialForRun
AS

SELECT     exp.Material.RowId, exp.Material.LSID, exp.Material.Name, exp.Material.SourceApplicationId, exp.Material.SourceProtocolLSID, exp.Material.RunId, exp.Material.Created, 
                      PAStartNode.RowId AS ApplicationID, PAStartNode.LSID AS ApplicationLSID, PAStartNode.Name AS ApplicationName, 
                      PAStartNode.CpasType AS ApplicationCpasType
FROM         exp.Material INNER JOIN
                      exp.MaterialInput ON exp.Material.RowId = exp.MaterialInput.MaterialId INNER JOIN
                      exp.ProtocolApplication PAMarkOutputNode ON exp.MaterialInput.TargetApplicationId = PAMarkOutputNode.RowId INNER JOIN
                      exp.ProtocolApplication PAStartNode ON PAMarkOutputNode.RunId = PAStartNode.RunId
WHERE     (PAMarkOutputNode.CpasType = N'ExperimentRunOutput') AND (PAStartNode.CpasType = N'ExperimentRun')

GO

CREATE VIEW exp.MarkedOutputDataForRun
AS

SELECT     exp.Data.RowId, exp.Data.LSID, exp.Data.Name, exp.Data.SourceApplicationId, exp.Data.SourceProtocolLSID, exp.Data.DataFileUrl, exp.Data.RunId, exp.Data.Created, 
                      PAStartNode.RowId AS ApplicationID, PAStartNode.LSID AS ApplicationLSID, PAStartNode.Name AS ApplicationName, 
                      PAStartNode.CpasType AS ApplicationCpasType
FROM         exp.Data INNER JOIN
                      exp.DataInput ON exp.Data.RowId = exp.DataInput.DataId INNER JOIN
                      exp.ProtocolApplication PAMarkOutputNode ON exp.DataInput.TargetApplicationId = PAMarkOutputNode.RowId INNER JOIN
                      exp.ProtocolApplication PAStartNode ON PAMarkOutputNode.RunId = PAStartNode.RunId
WHERE     (PAMarkOutputNode.CpasType = N'ExperimentRunOutput') AND (PAStartNode.CpasType = N'ExperimentRun')

GO

CREATE VIEW exp.OutputMaterialForNode
AS

SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.ChildMaterialforApplication
UNION ALL
SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.MarkedOutputMaterialForRun

GO

CREATE VIEW exp.OutputDataForNode
AS

SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.ChildDataforApplication
UNION ALL
SELECT     RowId, LSID, Name, SourceApplicationId, SourceProtocolLSID,  DataFileUrl, RunId, Created, ApplicationID, ApplicationLSID, ApplicationName
FROM         exp.MarkedOutputDataForRun

GO


