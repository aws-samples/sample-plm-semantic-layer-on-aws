// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Creates the databases and login roles on the Aurora cluster through the RDS
// Data API: each database is owned by its own role (fr_plm by fr_app, ...,
// atelier_core by core_app); a database may also name a read-only role that gets
// CONNECT, USAGE on public and SELECT on every table the owner creates later
// (Flyway runs as the owner). Idempotent: an existing database or role is kept,
// and every role password is re-synced from its secret on each run.
import { RDSDataClient, ExecuteStatementCommand } from '@aws-sdk/client-rds-data';
import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';
import type { CloudFormationCustomResourceEvent } from 'aws-lambda';

const rdsData = new RDSDataClient({});
const secrets = new SecretsManagerClient({});

const CLUSTER_ARN = process.env.CLUSTER_ARN!;
const ADMIN_SECRET_ARN = process.env.ADMIN_SECRET_ARN!;

interface Login {
  role: string;
  secretArn: string;
}
interface Database extends Login {
  reader?: Login;
}
const DATABASES = JSON.parse(process.env.DATABASES!) as Record<string, Database>;

async function sql(statement: string, database = 'postgres') {
  // A freshly resumed Serverless v2 writer can refuse the first statements.
  for (let attempt = 1; ; attempt++) {
    try {
      return await rdsData.send(
        new ExecuteStatementCommand({
          resourceArn: CLUSTER_ARN,
          secretArn: ADMIN_SECRET_ARN,
          database,
          sql: statement,
        }),
      );
    } catch (err) {
      const name = (err as Error).name;
      if (attempt >= 10 || !/DatabaseResumingException|DatabaseNotFoundException|StatementTimeout|Communications/.test(`${name} ${(err as Error).message}`)) throw err;
      await new Promise((r) => setTimeout(r, 5000 * attempt));
    }
  }
}

async function exists(query: string): Promise<boolean> {
  const res = await sql(query);
  return (res.records?.length ?? 0) > 0;
}

function ident(name: string): string {
  if (!/^[a-z][a-z0-9_]*$/.test(name)) throw new Error(`Unsafe identifier: ${name}`);
  return name;
}

function literal(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}

async function ensureRole(login: Login): Promise<string> {
  const role = ident(login.role);
  const secret = await secrets.send(new GetSecretValueCommand({ SecretId: login.secretArn }));
  const password = JSON.parse(secret.SecretString!).password as string;
  if (!(await exists(`SELECT 1 FROM pg_roles WHERE rolname = ${literal(role)}`))) {
    await sql(`CREATE ROLE ${role} LOGIN PASSWORD ${literal(password)}`);
  } else {
    await sql(`ALTER ROLE ${role} WITH LOGIN PASSWORD ${literal(password)}`);
  }
  return role;
}

async function ensureDatabase(name: string, spec: Database) {
  const db = ident(name);
  const owner = await ensureRole(spec);
  // PostgreSQL 16: creating a database OWNED BY a role requires membership in it.
  await sql(`GRANT ${owner} TO CURRENT_USER`);
  if (!(await exists(`SELECT 1 FROM pg_database WHERE datname = ${literal(db)}`))) {
    await sql(`CREATE DATABASE ${db} OWNER ${owner}`);
  }
  await sql(`REVOKE CONNECT ON DATABASE ${db} FROM PUBLIC`);
  await sql(`GRANT CONNECT ON DATABASE ${db} TO ${owner}`);
  await sql(`ALTER SCHEMA public OWNER TO ${owner}`, db);

  if (spec.reader) {
    const reader = await ensureRole(spec.reader);
    await sql(`GRANT CONNECT ON DATABASE ${db} TO ${reader}`);
    await sql(`GRANT USAGE ON SCHEMA public TO ${reader}`, db);
    // Tables appear later, created by the owner; each one is readable on creation.
    await sql(`ALTER DEFAULT PRIVILEGES FOR ROLE ${owner} IN SCHEMA public GRANT SELECT ON TABLES TO ${reader}`, db);
  }
}

export async function handler(event: CloudFormationCustomResourceEvent) {
  if (event.RequestType === 'Delete') return { PhysicalResourceId: event.PhysicalResourceId };
  for (const [name, spec] of Object.entries(DATABASES)) await ensureDatabase(name, spec);
  return { PhysicalResourceId: 'plm-databases' };
}
