# Branch Protection Baseline

Use these GitHub branch protection settings for `main`:

1. Require a pull request before merging.
2. Require approvals: at least 1.
3. Dismiss stale approvals when new commits are pushed.
4. Require status checks to pass before merging:
   - `test (17)`
   - `test (21)`
   - `security-scan`
5. Require branches to be up to date before merging.
6. Require conversation resolution before merging.
7. Restrict force pushes and branch deletion.

Optional hardening:

1. Require signed commits.
2. Require merge queue for busy repos.
3. Restrict who can push to `main`.
