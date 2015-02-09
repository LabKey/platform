-- These are used to test several different synonym scenarios

CREATE SYNONYM test.TestTable3 FOR test.TestTable;                  -- Table in test schema
CREATE SYNONYM test.Containers2 FOR core.Containers;                -- Table in another schema, with an FK to itself
CREATE SYNONYM test.ContainerAliases2 FOR core.ContainerAliases;    -- Table in another schema, with an FK to the synonym above
CREATE SYNONYM test.Users2 FOR core.Users;                          -- View in another schema