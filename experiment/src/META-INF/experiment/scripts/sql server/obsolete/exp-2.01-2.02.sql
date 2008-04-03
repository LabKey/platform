exec core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IDX_ObjectProperty_FloatValue'
go
exec core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject'
go

CREATE  INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId)
go