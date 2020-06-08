# LabKey Issues Module

The LabKey Issues module provides an issue tracker, a centralized workflow system for tracking issues or tasks across 
the lifespan of a project. Users can use the issue tracker to assign tasks to themselves or others, and follow the 
task through the work process from start to completion.

For further details about the usage and workflow see the following documentation:
[LabKey Issue/Bug Tracking].

[LabKey Issue/Bug Tracking]: https://www.labkey.org/Documentation/wiki-page.view?name=issues

### LabKey React pages

This module is setup to use `webpack` to build client application pages, mostly for developing 
with `React` components and `@labkey/components` shared components. The artifacts will be generated 
and placed into the standard LabKey view locations to make the pages available to the server through 
the `issues` controller. The generated `<entryPoint>.html` and `<entryPoint>.view.xml` files will 
be placed in the `issues/resources/views` directory and the generated JS/CSS artifacts will be 
placed in the `issues/resources/web/issues/gen` directory.

For further details about developing React based pages see the README doc in `/server/webpack`:
"LabKey React Page Development".