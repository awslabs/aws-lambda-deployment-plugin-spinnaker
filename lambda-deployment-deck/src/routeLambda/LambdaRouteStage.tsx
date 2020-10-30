// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import {
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  FormikStageConfig,
  FormValidator,
  HelpContentsRegistry,
  IExecutionDetailsSectionProps,
  IFormikStageConfigInjectedProps,
  IStage,
  IStageConfigProps,
  IStageTypeConfig,
  StageFailureMessage,
} from '@spinnaker/core';

import './LambdaRouteStage.less';

import { RouteLambdaFunctionStageForm } from './RouteLambdaFunctionStageForm';

export function RouteLambdaExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage, current, name } = props;

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <StageFailureMessage stage={stage} message={stage.outputs.failureMessage} />
      <div>
        <p> Function Name: {stage.outputs.functionName ? stage.outputs.functionName : "N/A"} </p>
        <p> Deployed Alias: {stage.outputs["deployment:aliasDeployed"] ? stage.outputs["deployment:aliasDeployed"] : "N/A"} </p>
        <p> Deployed Major Version: {stage.outputs["deployment:majorVersionDeployed"] ? stage.outputs["deployment:majorVersionDeployed"] : "N/A"} </p>
      </div>
    </ExecutionDetailsSection>
  );
}

/*
  IStageConfigProps defines properties passed to all Spinnaker Stages.
  See IStageConfigProps.ts (https://github.com/spinnaker/deck/blob/master/app/scripts/modules/core/src/pipeline/config/stages/common/IStageConfigProps.ts) for a complete list of properties.
  Pass a JSON object to the `updateStageField` method to add the `account` to the Stage.

  This method returns JSX (https://reactjs.org/docs/introducing-jsx.html) that gets displayed in the Spinnaker UI.
 */
function RouteLambdaConfig(props: IStageConfigProps) {
  return (
    <div className="RouteLambdaStageConfig">
      <FormikStageConfig
        {...props}
        validate={validate}
        onChange={props.updateStage}
        render={(props: IFormikStageConfigInjectedProps) => <RouteLambdaFunctionStageForm {...props} />}  
      />
    </div>
  );
}

/*
  This is a contrived example of how to use an `initialize` function to hook into arbitrary Deck services. 
  This `initialize` function provides the help field text for the `LambdaDeploymentConfig` stage form defined above.

  You can hook into any service exported by the `@spinnaker/core` NPM module, e.g.:
   - CloudProviderRegistry
   - DeploymentStrategyRegistry

  When you use a registry, you are diving into Deck's implementation to add functionality. 
  These registries and their methods may change without warning.
*/
export const initialize = () => {
  HelpContentsRegistry.register('aws.lambdaDeploymentStage.lambda', 'Lambda Name');
};

function validate(stageConfig: IStage) {
  const validator = new FormValidator(stageConfig);
  validator
    .field('trafficPercentA', 'Traffic Percent A')
    .required()  

  return validator.validateForm();
}

export namespace RouteLambdaExecutionDetails {
  export const title = 'Route Lambda Traffic Stage';
}

/*
  Define Spinnaker Stages with IStageTypeConfig.
  Required options: https://github.com/spinnaker/deck/master/app/scripts/modules/core/src/domain/IStageTypeConfig.ts
  - label -> The name of the Stage
  - description -> Long form that describes what the Stage actually does
  - key -> A unique name for the Stage in the UI; ties to Orca backend
  - component -> The rendered React component
  - validateFn -> A validation function for the stage config form.
 */
export const lambdaRouteStage: IStageTypeConfig = {
  key: 'Aws.LambdaTrafficRoutingStage',
  label: `AWS Lambda Route`,
  description: 'Route traffic across various versions of your Lambda function',
  component: RouteLambdaConfig, // stage config
  executionDetailsSections: [RouteLambdaExecutionDetails, ExecutionDetailsTasks],
  validateFn: validate,
};
