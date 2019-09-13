# Summary
This is a demo LabKey ReactJS module. This module demonstrates how to render a React application within the LabKey framework 
including keeping the standard LabKey header and header menus.

<a name="gettingStarted"></a>
# Getting Started
Build this module using the [LabKey gradle build]. This will install necessary packages, generate resources and put resources in correct LabKey locations.

There are two example React pages at
- http://localhost:8080/labkey/home/demo-helloWorld.view?
- http://localhost:8080/labkey/home/demo-todoList.view?


<a name="functionality"></a>
# Functionality Overview
This module is built with the standard LabKey Gradle build. Gradle will use the Node and NPM version defined in \<projectHome\>/gradle.properties 
to run the "build-prod" script defined in package.json.

The webpack production build will compile typescript and javascript files, as well as css and scss files and bundle them independently 
for each webpack entry point.  The bundles are placed in the required LabKey directory for web resources.  The webpack build will also 
generate the necessary LabKey html files, with the containing element of the React apps, as well as the necessary view.xml files 
to make the bundled React and css files available to the appropriate apps.

<a name="devServer"></a>
## Development Server
This module includes a webpack development server to help with rapid development.  The server is setup for Hot Module Replacement, 
to allow updates made to both typescript and scss files to take effect on the page without manual builds or page refreshes. To 
start the server, from the command line run "npm start", then navigate to either of the appropriate development pages.
- http://localhost:8080/labkey/home/demo-helloWorldDev.view?
- http://localhost:8080/labkey/home/demo-todoListDev.view?

<a name="jest"></a>
## Jest Tests
This module is setup to run Jest tests, including using Enzyme and Jest Snapshot testing. There is an example jest test with 
snapshot in the HelloWorld module.  Jest tests can be ran using "npm run test" or they can be ran directly in IntelliJ.

    
[LabKey gradle build]: https://www.labkey.org/Documentation/wiki-page.view?name=gradleBuild    