-- fix inconsistent null status on the Name column.  Incrementals and base scripts differed.
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Name NVARCHAR(200) NULL
go
ALTER TABLE exp.MaterialSource
   DROP CONSTRAINT UQ_MaterialSource_Name 
GO

