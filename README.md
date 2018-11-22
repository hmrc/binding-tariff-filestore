
# binding-tariff-filestore

A microservice providing read, write and delete access to Amazon S3.


### Running

##### To run this Service you will need:

1) [Service Manager](https://github.com/hmrc/service-manager) Installed
2) [SBT](https://www.scala-sbt.org) Version `>0.13.13` Installed
3) [LocalStack](https://github.com/localstack/localstack) Installed
4) [AWS CLI](https://aws.amazon.com/cli/) Installed

##### Starting the microservice:

###### The first time you run the app

Start LocalStack `SERVICES=s3 localstack start`

Run the LocalStack AWS Set Up Script `./initialize-localstack.sh`

###### From then on

Run `sbt run`

##### Starting With Service Manager

This application runs on port 9583

Run `sm --start BINDING_TARIFF_FILESTORE -r`

### Testing

Run `./run_all_tests.sh`

or `sbt test it:test`


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
