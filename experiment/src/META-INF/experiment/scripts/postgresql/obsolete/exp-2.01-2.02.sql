SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IDX_ObjectProperty_FloatValue')
;
SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject')
;

CREATE  INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId)
;


