UPDATE exp.Protocol SET Status = 'Active' WHERE ApplicationType = 'ExperimentRun' AND Status IS NULL;