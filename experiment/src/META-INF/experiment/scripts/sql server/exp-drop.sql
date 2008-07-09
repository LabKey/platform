-- DROP current views.
EXEC core.fn_dropifexists 'MaterialSourceWithProject', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ObjectPropertiesView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunMaterialOutputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ObjectClasses', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'AllLsidContainers', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunDataInputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunMaterialInputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExperimentRunDataOutputs', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'AllLsid', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'OutputDataForNode', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'OutputMaterialForNode', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'MarkedOutputDataForRun', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'MarkedOutputMaterialForRun', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ChildDataForApplication', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ChildMaterialForApplication', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorAllDataView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorRunStartDataView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorOutputDataView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorAllMaterialsView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorRunStartMaterialsView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'PredecessorOutputMaterialsView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ProtocolActionPredecessorLSIDView', 'exp', 'VIEW', NULL
EXEC core.fn_dropifexists 'ProtocolActionStepDetailsView', 'exp', 'VIEW', NULL
GO
