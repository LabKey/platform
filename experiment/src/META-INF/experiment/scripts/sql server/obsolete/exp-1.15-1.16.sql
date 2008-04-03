ALTER TABLE exp.ProtocolParameter ALTER COLUMN StringValue nvarchar(4000) NULL
go
UPDATE exp.ProtocolParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink'
go
ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue
go
ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue
go

ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue nvarchar(4000) NULL
go
UPDATE exp.ProtocolApplicationParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink'
go
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue
go
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue
go
