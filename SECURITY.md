# Security

Report vulnerabilities through this repository's private GitHub Security Advisory flow. Do not put credentials, access tokens, or personal data in public issues or pull requests.

The app uses a public FAM browser client and validates bearer access tokens in the backend. OpenShift credentials belong in GitHub secrets. Business endpoints must enforce an approved access policy, including capability, record scope and workflow-state checks.

## Public repository content

Keep credentials, private keys, local connection profiles, real appraisal records, legacy SVN
archives and database exports out of commits, pull requests, logs, artifacts and image build contexts.
Environment-specific account/grant inventories, provisioning updates and operational handoffs belong
in private team records. Public configuration examples must use placeholders or reserved example domains.

Synthetic records do not by themselves make inherited schema identifiers or business rules suitable
for publication. Confirm their release classification with the system owner when adapting private
legacy material. A clean secret scan does not establish that approval.

If restricted material was pushed, report it privately and assess published history, pull-request
snapshots, artifacts and images as well as the current files. Removing it in a later commit does not
erase earlier copies.
