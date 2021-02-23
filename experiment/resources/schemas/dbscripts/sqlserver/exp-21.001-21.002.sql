ALTER TABLE exp.PropertyDescriptor ADD MaterialPropertyType NVARCHAR(20) NULL;
ALTER TABLE exp.Material ADD RootMaterialLSID LSIDtype NULL;
ALTER TABLE exp.Material ADD AliquotedFromLSID LSIDtype NULL;
GO