ALTER TABLE exp.PropertyDescriptor ADD DerivationDataScope NVARCHAR(20) NULL;
ALTER TABLE exp.Material ADD RootMaterialLSID LSIDtype NULL;
ALTER TABLE exp.Material ADD AliquotedFromLSID LSIDtype NULL;
GO