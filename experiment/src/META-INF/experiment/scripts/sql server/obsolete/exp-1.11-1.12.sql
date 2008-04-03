ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Name NVARCHAR(200) NULL
go
-- NOTE: | delimited list of semantic types, could normalize this...
ALTER TABLE exp.PropertyDescriptor ADD SemanticType NVARCHAR(200)
go
