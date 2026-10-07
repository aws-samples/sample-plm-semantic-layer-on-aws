# `infra/policies/` — the deploy role

The least-privilege IAM role that `infra/scripts/deploy.sh`, `infra/scripts/build-images.sh`
and `tests/smoke.mjs` run under when a CI runner or a workstation deploys the sample.
Two documents, applied once with an administrator profile of the target account;
replace `111111111111` with that account id and `eu-west-1` with the deployment region
in both files first:

```bash
aws iam create-role --profile <admin profile> \
  --role-name AtelierDeploy \
  --assume-role-policy-document file://infra/policies/deploy-role-trust.json \
  --tags Key=Demo,Value=plm-semantic-layer-sample

aws iam put-role-policy --profile <admin profile> \
  --role-name AtelierDeploy \
  --policy-name AtelierDeployPermissions \
  --policy-document file://infra/policies/deploy-role-permissions.json
```

Both files are strict IAM JSON: IAM rejects a document carrying any extra top-level
key, so the commentary lives here.

## `deploy-role-trust.json`: pin the principal

The placeholder principal `arn:aws:iam::111111111111:role/REPLACE_WITH_DEPLOYER_PRINCIPAL`
must become the one identity allowed to assume the role:

- **A workstation**: the ARN of the IAM role or user the deployer's AWS profile resolves
  to (`aws sts get-caller-identity --profile <profile>`).
- **GitHub Actions**: the account's GitHub OIDC provider, pinned to one repository and
  branch, in place of the `AWS` principal:

  ```json
  {
    "Sid": "GitHubActions",
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::111111111111:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:<org>/<repo>:ref:refs/heads/main"
      }
    }
  }
  ```

Never leave the trust policy without a condition or with a whole account as principal:
every identity in that account could then deploy.

## `gitlab-deploy-trust.json`: GitLab CI

A GitLab CI pipeline runs on runners whose credential broker assumes a fixed runner
role, tags the session with the GitLab group and project of the pipeline, and then
assumes the role named in the CI/CD variable `AWS_CREDS_TARGET_ROLE`. This trust policy
admits that runner role on two conditions, `aws:PrincipalTag/GitLab:Group` and
`aws:PrincipalTag/GitLab:Project`, so that no other project on the same runner fleet can
assume the deploy role. The broker tags the session, so the policy grants `sts:TagSession`
as well as `sts:AssumeRole`; without it the assume fails. Replace the placeholders, then
create the role with this file in place of `deploy-role-trust.json`:

- `arn:aws:iam::111111111111:role/REPLACE_WITH_RUNNER_ROLE`: the runner role of the
  GitLab instance.
- `REPLACE_WITH_GITLAB_GROUP`, `REPLACE_WITH_GITLAB_PROJECT`: the group and project path of the
  repository.

```bash
aws iam create-role --profile <admin profile> \
  --role-name AtelierGitLabDeploy \
  --assume-role-policy-document file://gitlab-deploy-trust.json \
  --tags Key=Demo,Value=plm-semantic-layer-sample

aws iam put-role-policy --profile <admin profile> \
  --role-name AtelierGitLabDeploy \
  --policy-name AtelierDeployPermissions \
  --policy-document file://deploy-role-permissions.json
```

Set the role ARN as the project's CI/CD variable `AWS_CREDS_TARGET_ROLE`. The pipeline
assumes the role in every job and reads no static key.

## `deploy-role-permissions.json`: why it is more than the `cdk-*` assume

Statement `CloudFormationDeploy` grants, on the `Atelier*` stack ARNs, the CloudFormation actions
the CDK CLI calls with the caller's credentials while CloudFormation itself runs as the bootstrap
execution role: the change-set cycle (`CreateChangeSet`, `DescribeChangeSet`, `ExecuteChangeSet`,
`DeleteChangeSet`, `ListChangeSets`), the direct and recovery paths (`CreateStack`, `UpdateStack`,
`RollbackStack`, `ContinueUpdateRollback`), the reads the CLI makes while it compares and waits
(`DescribeStacks`, `DescribeStackEvents`, `DescribeStackResources`, `ListStackResources`,
`ListStacks`, `GetTemplate`, `GetTemplateSummary`) and the teardown (`DeleteStack`,
`UpdateTerminationProtection`). The list is the CDK bootstrap deploy role's
(`cdk-hnb659fds-deploy-role`, in the CDK CLI's bootstrap template) without the stack-refactor
actions, which the sample does not use, and covers every CloudFormation call the CLI's deploy,
destroy and rollback paths make; the role cannot set or change a stack policy.

Statement `CdkToolkitRead` grants `cloudformation:DescribeStacks` on the `CDKToolkit` stack of
both regions, the one call the CLI makes on the bootstrap stack: it reads the stack's outputs
(bootstrap version, assets bucket, image repository) before every deploy. The bootstrap stack is
created and updated by `cdk bootstrap` under an administrator profile, never by this role, so the
role cannot change or delete it.

CDK prefers to assume the `cdk-hnb659fds-*` bootstrap roles (`CdkAssumeBootstrapRoles`).
The statement grants `sts:TagSession` as well: a session that carries transitive session tags,
as the GitLab runner's does, cannot assume another role without it, and the synth's
availability-zone lookup then fails on `ec2:DescribeAvailabilityZones`.
When those roles do not trust the deploy role, CDK prints

> current credentials could not be used to assume '…cdk-hnb659fds-deploy-role…',
> but are for the right account. Proceeding anyway

and falls back to the role's own credentials for the rest of the deploy, so the role
carries the deploy permissions itself: CloudFormation on the `Atelier*` stacks, the
bootstrap SSM parameter, asset publishing to the CDK bucket, `iam:PassRole` on the
CloudFormation execution role, image push to the `atelier/*` ECR repositories, the
runtime `config.json` upload and CloudFront invalidation, the origin secret and a read of
the CAD bucket's objects for the smoke test, and the security-group rule that lets the CloudFront VPC origin reach the agent ALB.
Asset publishing is object access only (`PutObject`, `GetObject`, `DeleteObject`,
`ListBucket`, `GetBucketLocation`): `cdk bootstrap` creates the assets bucket and owns its
policy, versioning, encryption, lifecycle and public-access block, so the role cannot create
or reconfigure it. `infra/policies/deploy-role-permissions.test.mts` (`npm test -w infra`)
pins that statement and the describe calls.

| Deploy symptom | Statement |
|---|---|
| `Proceeding anyway` then the deploy fails | the direct statements are missing; restore this file |
| `Proceeding anyway` and the deploy succeeds | expected: CDK fell back to the direct permissions |
| `not authorized to perform: cloudformation:CreateChangeSet` | `CloudFormationDeploy` |
| `not authorized to perform: cloudformation:DescribeStacks` on `stack/CDKToolkit` | `CdkToolkitRead` |
| `AccessDenied` publishing to the CDK assets bucket | `CdkAssetPublish` |
| `not authorized to perform: ssm:GetParameter … /cdk-bootstrap/version` | `SsmForCdkBootstrap` |
| `iam:PassRole` denied | `PassCfnExecRole` |
| `ecr:InitiateLayerUpload` denied | `EcrPushImages` |
| smoke: `head-object` on a STEP file answers 403 | `SmokeReadsCad` |
