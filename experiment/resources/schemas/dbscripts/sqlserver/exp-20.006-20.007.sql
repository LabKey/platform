EXEC sp_rename 'exp.propertyvalidator', 'pv_old'
GO

CREATE TABLE exp.PropertyValidator
(
    RowId        int identity(1,1) not null,
    Name         varchar(50) not null,
    Description  varchar(200),
    TypeURI      varchar(200) not null,
    Expression   text,
    ErrorMessage text,
    Properties   text,
    Container    entityid not null constraint fk_pv_container references core.containers (entityid),
    PropertyId   int not null constraint fk_pv_descriptor references exp.propertydescriptor,
    constraint pk_propertyvalidator primary key (container, propertyid, rowid)
);
GO

SET IDENTITY_INSERT exp.propertyvalidator ON;
INSERT INTO exp.propertyvalidator(propertyid, rowid, name, description, typeuri, expression, properties, errormessage, container)
SELECT VR.propertyid, PV.rowid, PV.name, PV.description, PV.typeuri, PV.expression, PV.properties, PV.errormessage, PV.container
FROM exp.pv_old PV INNER JOIN exp.validatorreference VR ON PV.rowid = VR.validatorid
ORDER BY container, propertyid, rowid;
SET IDENTITY_INSERT exp.propertyvalidator OFF;

DROP TABLE exp.validatorreference;
DROP TABLE exp.pv_old;
GO