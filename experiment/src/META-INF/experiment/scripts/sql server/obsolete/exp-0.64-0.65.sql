DROP VIEW exp.ProtocolActionStepDetailsView
GO

CREATE VIEW exp.ProtocolActionStepDetailsView AS
SELECT     Protocol_P.LSID AS ParentProtocolLSID, Protocol_C.LSID AS LSID, Protocol_C.LSID AS ChildProtocolLSID, exp.ProtocolAction.Sequence AS Sequence, exp.ProtocolAction.Sequence AS ActionSequence,
                      exp.ProtocolAction.RowId AS ActionId, Protocol_C.RowId AS RowId, Protocol_C.Name AS Name,
              Protocol_C.ProtocolDescription AS ProtocolDescription, Protocol_C.ApplicationType AS ApplicationType,
                      Protocol_C.MaxInputMaterialPerInstance AS MaxInputMaterialPerInstance, Protocol_C.MaxInputDataPerInstance AS MaxInputDataPerInstance,
                      Protocol_C.OutputMaterialPerInstance AS OutputMaterialPerInstance, Protocol_C.OutputDataPerInstance AS OutputDataPerInstance, Protocol_C.OutputMaterialType AS OutputMaterialType, Protocol_C.OutputDataType AS OutputDataType,
                      Protocol_C.Instrument AS Instrument, Protocol_C.Software AS Software, Protocol_C.contactId AS contactId,
                      Protocol_C.Created AS Created, Protocol_C.EntityId AS EntityId, Protocol_C.CreatedBy AS CreatedBy, Protocol_C.Modified AS Modified, Protocol_C.ModifiedBy AS ModifiedBy, Protocol_C.Container AS Container
FROM         exp.Protocol Protocol_C INNER JOIN
                      exp.ProtocolAction ON Protocol_C.RowId = exp.ProtocolAction.ChildProtocolId INNER JOIN
                      exp.Protocol Protocol_P ON exp.ProtocolAction.ParentProtocolId = Protocol_P.RowId
GO


ALTER TABLE exp.PropertyDescriptor
    ADD ValueType nvarchar(50) NULL
GO

ALTER TABLE exp.Material
    ALTER COLUMN CpasType NVARCHAR(200) NULL
GO
