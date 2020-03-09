# Deployment guide

## Requirements:

* [Docker Desktop](https://www.docker.com/products/docker-desktop) - Required to build docker image locally before pushing to a repo
* [Maven v3.6.3+](https://maven.apache.org/) - Required to build JAR as executable file
* [SAM CLI v0.41.0+](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html) - To package/deploy SAM apps.
* [AWS CLI v1.18.0+](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html) - To create resources in your AWS account.



### Initial credentials in `AWS SSM Parameter Store`
```
export SANDBOX_UNAME="YOUR_SANDBOX_USERNAME"
export SANDBOX_PWORD="YOUR_SANDBOX_PASSWORD"
export SANDBOX_DOMAIN="test"
export SANDBOX_ACTIVE="0"
export SANDBOX_SECURITY_TOKEN="YOUR_SANDBOX_SECURITY_TOKEN"

export UNAME="SFDC_USERNAME"
export PWORD="SFDC_PASSWORD"
export SECURITY_TOKEN="SFDC_SECURITY_TOKEN"

export AWS_ACCOUNT_ID="YOUR_AWS_ACCOUNT_ID"
export AWS_DEFAULT_REGION="YOUR_TARGET_REGION"

aws ssm put-parameter \
    --name "/sfdc/sandbox/active" \
    --type "String" \
    --value $SANDBOX_ACTIVE \
    --overwrite

    
aws ssm put-parameter \
    --name "/sfdc/sandbox/domain" \
    --type "String" \
    --value $SANDBOX_DOMAIN \
    --overwrite

aws ssm put-parameter \
    --name "/sfdc/sandbox/username" \
    --type "SecureString" \
    --value $SANDBOX_UNAME \
    --overwrite

aws ssm put-parameter \
    --name "/sfdc/sandbox/password" \
    --type "SecureString" \
    --value $SANDBOX_PWORD \
    --overwrite
    
aws ssm put-parameter \
    --name "/sfdc/sandbox/security_token" \
    --type "SecureString" \
    --value $SANDBOX_SECURITY_TOKEN \
    --overwrite

aws ssm put-parameter \
    --name "/sfdc/username" \
    --type "SecureString" \
    --value $UNAME \
    --overwrite

aws ssm put-parameter \
    --name "/sfdc/password" \
    --type "SecureString" \
    --value $PWORD \
    --overwrite

aws ssm put-parameter \
    --name "/sfdc/security_token" \
    --type "SecureString" \
    --value $SECURITY_TOKEN \
    --overwrite
```

### Deploy `Subscriber`

```
export DOCKER_IMAGE_NAME="sfdc-cdc-aws-sub"

export STACK_NAME="sfdc-cdc-aws"

cd subscriber/app

mvn clean package

docker build -t $DOCKER_IMAGE_NAME .

aws ecr get-login-password | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$DOCKER_IMAGE_NAME

docker tag $DOCKER_IMAGE_NAME:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$DOCKER_IMAGE_NAME:latest

docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$DOCKER_IMAGE_NAME:latest

cd ../

aws cloudformation deploy --stack-name $STACK_NAME-vpc --parameter-overrides EnvironmentName=$STACK_NAME --template-file ./cloudformation/vpc.yaml --capabilities CAPABILITY_IAM

aws cloudformation deploy --stack-name $STACK_NAME-subscriber --parameter-overrides VPCStackName="$STACK_NAME-vpc" DockerImageUrl="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$DOCKER_IMAGE_NAME:latest" --template-file ./cloudformation/subscriber.yaml --capabilities CAPABILITY_IAM

cd ../

```

### Deploy `Worker`

```
cd worker

export SAM_BACKET_NAME="$STACK_NAME-$AWS_DEFAULT_REGION-sam"
export TARGET_BUCKET_NAME="YOUR_TARGET_S3_BUCKET_NAME"

aws s3 mb s3://$SAM_BACKET_NAME

sam build

sam package --s3-bucket $SAM_BACKET_NAME --output-template-file packaged.yaml 

aws cloudformation deploy --stack-name $STACK_NAME-worker --parameter-overrides SFDCSubscriberStack="$STACK_NAME-subscriber" TargetS3BucketName="TARGET_BUCKET_NAME" --template-file ./packaged.yaml --capabilities CAPABILITY_IAM
```