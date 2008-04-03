exec core.fn_dropifexists 'Experiment', 'exp', 'INDEX', 'IX_Experiment_Container'
go
exec core.fn_dropifexists 'ExperimentRun', 'exp', 'INDEX', 'IX_CL_ExperimentRun_Container'
go
exec core.fn_dropifexists 'ExperimentRun', 'exp', 'INDEX', 'IX_ExperimentRun_ProtocolLSID'
go
exec core.fn_dropifexists 'RunList', 'exp', 'INDEX', 'IX_RunList_ExperimentRunId'
go
exec core.fn_dropifexists 'ProtocolApplicationParameter', 'exp', 'INDEX', 'IX_ProtocolApplicationParameter_AppId'
go
exec core.fn_dropifexists 'ProtocolActionPredecessor', 'exp', 'INDEX', 'IX_ProtocolActionPredecessor_PredecessorId'
go
exec core.fn_dropifexists 'ProtocolAction', 'exp', 'INDEX', 'IX_ProtocolAction_ChildProtocolId'
go
exec core.fn_dropifexists 'ProtocolParameter', 'exp', 'INDEX', 'IX_ProtocolParameter_ProtocolId'
go
exec core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_Container'
go
exec core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId'
go
exec core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_DataFileUrl'
go
exec core.fn_dropifexists 'DataInput', 'exp', 'INDEX', 'IX_DataInput_PropertyId'
go
exec core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_Container'
go
exec core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_SourceApplicationId'
go
exec core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_CpasType'
go
exec core.fn_dropifexists 'MaterialInput', 'exp', 'INDEX', 'IX_MaterialInput_PropertyId'
go
exec core.fn_dropifexists 'MaterialSource', 'exp', 'INDEX', 'IX_MaterialSource_Container'
go
exec core.fn_dropifexists 'ActiveMaterialSource', 'exp', 'INDEX', 'IX_ActiveMaterialSource_MaterialSourceLSID'
go
exec core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject'
go
exec core.fn_dropifexists 'PropertyDomain', 'exp', 'INDEX', 'IX_PropertyDomain_DomainId'
go
exec core.fn_dropifexists 'PropertyDescriptor', 'exp', 'INDEX', 'IX_PropertyDescriptor_Container'
go
exec core.fn_dropifexists 'DomainDescriptor', 'exp', 'INDEX', 'IX_DomainDescriptor_Container'
go
exec core.fn_dropifexists 'Object', 'exp', 'INDEX', 'IX_Object_OwnerObjectId'
go
-- redundant to unique constraint
exec core.fn_dropifexists 'Object', 'exp', 'INDEX', 'IX_Object_Uri'
go



CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container)
go
create clustered index IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container)
go
create index IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID)
go
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId)
go
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId)
go
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId on exp.ProtocolActionPredecessor(PredecessorId)
go
CREATE INDEX IX_ProtocolAction_ChildProtocolId on exp.ProtocolAction(ChildProtocolId)
go
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId)
go

CREATE INDEX IX_Data_Container ON exp.Data(Container)
go
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId)
go
CREATE INDEX IX_DataInput_PropertyId ON exp.DataInput(PropertyId)
go
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl)
go

CREATE INDEX IX_Material_Container ON exp.Material(Container)
go
CREATE INDEX IX_Material_SourceApplicationId ON exp.Material(SourceApplicationId)
go
CREATE INDEX IX_MaterialInput_PropertyId ON exp.MaterialInput(PropertyId)
go
CREATE INDEX IX_Material_CpasType ON exp.Material(CpasType)
go


CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container)
go
CREATE INDEX IX_ActiveMaterialSource_MaterialSourceLSID on exp.ActiveMaterialSource(MaterialSourceLSID)
go


CREATE  INDEX IX_ObjectProperty_PropertyObject ON exp.ObjectProperty(PropertyId, ObjectId)
go
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID)
go
CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container)
go
CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container)
go
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId)
go
