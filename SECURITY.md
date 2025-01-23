## Reporting a Vulnerability

If you discover a potential security issue in this project we ask that you notify OpenSearch Security directly via email to security@opensearch.org. Please do **not** create a public GitHub issue.

## SAML Auth Tokens Are Captured in Plain Text in Captured Traffic

In order to link SAML user session to user IDs and their requests, we capture SAML tokens from requests and store them in the captured traffic. These tokens are currently captured in plain text. A future release will update this functionality to mask the captured user tokens.
