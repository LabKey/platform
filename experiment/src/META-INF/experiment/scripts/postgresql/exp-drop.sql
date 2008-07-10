-- DROP obsolete views.  Do not remove; these are needed when upgrading from older versions.
SELECT core.fn_dropifexists('materialsource', 'cabig', 'VIEW', NULL);
SELECT core.fn_dropifexists('experimentrun', 'cabig', 'VIEW', NULL);

-- DROP current views.
SELECT core.fn_dropifexists('MaterialSourceWithProject', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ObjectPropertiesView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ExperimentRunMaterialOutputs', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ObjectClasses', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('AllLsidContainers', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ExperimentRunDataInputs', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ExperimentRunMaterialInputs', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ExperimentRunDataOutputs', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('AllLsid', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('OutputDataForNode', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('OutputMaterialForNode', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('MarkedOutputDataForRun', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('MarkedOutputMaterialForRun', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ChildDataForApplication', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ChildMaterialForApplication', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('PredecessorAllDataView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('PredecessorRunStartDataView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('PredecessorOutputDataView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('PredecessorAllMaterialsView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('PredecessorRunStartMaterialsView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('PredecessorOutputMaterialsView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ProtocolActionPredecessorLSIDView', 'exp', 'VIEW', NULL);
SELECT core.fn_dropifexists('ProtocolActionStepDetailsView', 'exp', 'VIEW', NULL);
