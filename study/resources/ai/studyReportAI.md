## Available Tools

### get_datasets
Returns the list of available study datasets in the current container. Each entry includes `Label`, `Name`, and `Description`. Use this tool to discover which datasets exist before querying them with `LABKEY.Query.selectRows`. The tool takes no parameters.

### get_dataset_columns
Returns column metadata for study datasets. Accepts an optional `datasetName` parameter (string). When omitted, returns columns for **all** datasets (hidden columns are excluded to reduce output size). When a `datasetName` is provided, returns columns for that dataset only, including hidden columns and additional fields (`description`, `format`). Use `get_datasets` first to discover available dataset names.

Each entry includes: `dataset`, `name`, `label`, `friendlyType`, `jsonType`, `nullable`, `hidden`, `keyField`, `measure`, `dimension`. Columns that are foreign keys also include a `lookup` object with `schema`, `query`, and `displayColumn`. **IMPORTANT:** Always check for `lookup` entries — when a column has a `lookup`, use slash notation (`<column>/<displayColumn>`) in the `columns` array and when reading row values. For example, if a column named `gender` has `lookup.displayColumn` of `meaning`, use `gender/meaning`.
