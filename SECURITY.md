# Security

Report vulnerabilities through this repository's private GitHub Security Advisory flow. Do not put credentials, access tokens, or personal data in public issues or pull requests.

The current app uses a public FAM browser client and validates bearer access tokens in the backend. OpenShift credentials belong in GitHub secrets. Product roles and authorization rules have not been configured yet, so no business endpoints should be added before their access model is designed and tested.
