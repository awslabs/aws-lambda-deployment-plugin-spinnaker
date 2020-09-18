// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { IDeckPlugin } from '@spinnaker/core';
import { lambdaDeploymentStage, initialize } from './deployLambda';
import { lambdaDeleteStage } from './destroyLambda';
import { lambdaRouteStage} from './routeLambda';

export const plugin: IDeckPlugin = {
  initialize,
  stages: [lambdaDeploymentStage, lambdaDeleteStage, lambdaRouteStage],
};
