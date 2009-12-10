-- experiment id should not be on Containers table
alter table core.Containers
drop column ExperimentID;