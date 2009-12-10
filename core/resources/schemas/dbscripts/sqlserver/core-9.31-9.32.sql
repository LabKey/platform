/* SQLServer Version */

alter table core.Containers
  add ExperimentID int,
  Description nvarchar(4000),
  constraint uq_containers_experimentid unique (Parent,ExperimentID);
