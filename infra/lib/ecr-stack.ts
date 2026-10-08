// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { RemovalPolicy, Stack, StackProps } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ecr from 'aws-cdk-lib/aws-ecr';

/** One image per service; infra/scripts/build-images.sh pushes `<repo>:<tag>`. */
export const IMAGES = ['plm-fr', 'plm-de', 'plm-uk', 'plm-es', 'plm-core', 'ontop', 'query', 'agent'] as const;
export type ImageName = (typeof IMAGES)[number];

export interface EcrStackProps extends StackProps {
  readonly prefix: string;
}

export function repoName(prefix: string, image: ImageName): string {
  return `${prefix.toLowerCase()}/${image}`;
}

/**
 * ECR repositories, deployed before the image build so CI can push into them.
 * The services stack imports them by name.
 */
export class EcrStack extends Stack {
  constructor(scope: Construct, id: string, props: EcrStackProps) {
    super(scope, id, props);

    for (const image of IMAGES) {
      new ecr.Repository(this, `Repo-${image}`, {
        repositoryName: repoName(props.prefix, image),
        imageScanOnPush: true,
        // A tag is pushed once: build-images.sh tags every image by the hash of its inputs.
        imageTagMutability: ecr.TagMutability.IMMUTABLE,
        encryption: ecr.RepositoryEncryption.AES_256,
        removalPolicy: RemovalPolicy.DESTROY,
        emptyOnDelete: true,
        // Keep the latest images: an idle demo must still be able to replace a task.
        lifecycleRules: [{ maxImageCount: 20 }],
      });
    }
  }
}
