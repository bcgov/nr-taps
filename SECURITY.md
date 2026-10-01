# Security

Report vulnerabilities through this repository's private GitHub Security Advisory flow. Do not put credentials, access tokens, or personal data in public issues or pull requests.

The app uses a public FAM browser client and validates bearer access tokens in the backend. OpenShift credentials belong in GitHub secrets. Business endpoints must enforce an approved access policy, including capability, record scope and workflow-state checks.
