
# Binding Tariff Filestore

A microservice providing read, write and delete access to Amazon S3.

### Running

##### To run this Service you will need:

1) [Service Manager](https://github.com/hmrc/service-manager) Installed
2) [SBT](https://www.scala-sbt.org) Version `>0.13.13` Installed
3) [LocalStack](https://github.com/localstack/localstack) Installed
4) [AWS CLI](https://aws.amazon.com/cli/) Installed

###### Installing localstack:

`pip install localstack --user --upgrade --ignore-installed six`

`pip install amazon_kclpy`

`localstack start`

Set up aws cli credentials (one time only):

```
aws configure
AWS Access Key ID [None]: test
AWS Secret Access Key [None]: <password here>
Default region name [None]: eu-west-2
Default output format [None]:
```

##### Starting the microservice:

###### The first time you run the app

Start LocalStack `SERVICES=s3 localstack start`

Run the LocalStack AWS Set Up Script `./initialize-localstack.sh`

###### From then on

1) Start Mongo
2) Start Upscan `sm --start UPSCAN_STUB -r`

Run `sbt run`

##### Starting With Service Manager

This application runs on port 9583

Run `sm --start BINDING_TARIFF_FILESTORE -r`

### Testing

Run `./run_all_tests.sh`

or `sbt test it:test`


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
