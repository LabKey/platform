/* PostgreSQL Version */

alter table core.Containers
  add ExperimentID int,
  add Description varchar(4000)
--  ,add constraint uq_containers_experimentid unique (Parent,ExperimentID);
