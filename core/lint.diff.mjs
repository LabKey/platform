import { execa } from 'execa';

const fileExtensions = ['.tsx', '.ts'];  // linted file types

// This is the difference from where the top of the git repo is and where this script is run from. Will
// need to be updated for other repos.
const repoPath = 'core/';

// Default path to the directory to be checked for changes
let lintPath = 'src/client/';

// Default lint target (--fix changes to lint-fix)
let npmTarget = 'lint';

let currentBranch = false;

// Parameters all optional.
// --fix: this will perform eslint --fix instead of regular eslint
// --currentBranch: this will perform eslint on the files that have been changed in this branch compared
//   to the master branch. Can be run with our without --fix. Will ignore any file paths passed in.
// File path: if wanting to do a lint-diff on a specific directory or file otherwise defaults to src/
if (process.argv.length > 2) {
    for (let i=2; i<process.argv.length; i++) {
        switch(process.argv[i]) {
            case '--fix':
                npmTarget = 'lint-fix';
                break;
            case '--currentBranch':
                currentBranch = true;
                break;
            default:
                lintPath = process.argv[i];
        }
    }
}

(async () => {
    let files;
    let stdout; // This is the supported way to pipe stdout to a local variable
    if (currentBranch) {
        // Get name of the current branch
        ({ stdout } = await execa('git', ['rev-parse', '--abbrev-ref', 'HEAD']));
        if (!stdout) {
            console.error('Error finding git branch name');
        } else {
            console.log('Checking for updated files in branch: ', stdout);
        }

        const branch = stdout;

        // Diff current branch against develop to get changed file names
        ({stdout} = await execa('git', ['diff', 'develop...' + branch, '--name-only', '--diff-filter=AM']));
        if (!stdout) {
            console.log('No changed files in branch ' + branch);
        }

        files = stdout;
    } else {
        // Diff uncommitted changes against committed to
        ({stdout} = await execa('git', ['diff', '--name-only', '--diff-filter=AM', lintPath]));
        if (!stdout) {
            console.log('No changed files at ' + lintPath);
        }

        files = stdout;
    }

    if (files) {
        let filtered = stdout.split('\n');

        // Filter by file extension and file path
        filtered = filtered.filter(file => {
            file = file.trim();
            const correctPath = file.startsWith(repoPath + 'src/');
            const correctExt = fileExtensions.findIndex(ext => (file.endsWith(ext))) !== -1;
            return correctPath && correctExt
        });

        if (filtered.length < 1) {
            console.log('No changed files match the file extension.')
        }
        else {
            // Remove file path relative to git repo
            filtered = filtered.map(file => {
                return file.substring(repoPath.length);
            });

            console.log('Linting files:\n', filtered);

            // File paths need parens to resolve correctly
            const param = "\"" + filtered.join("\" \"") + "\"";

            try {
                await execa('npm', ['run', npmTarget, param], {shell: true}).stdout.pipe(process.stdout);
            }
            catch (error) {
                console.error("Lint error: ", error);
            }
        }
    }
})();
