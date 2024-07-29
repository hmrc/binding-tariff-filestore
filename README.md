
# binding-tariff-filestore

The backend filestore service which manages attachment metadata and S3 bucket access for the Advance Tariff Rulings services.

### Running

##### To run this Service you will need:

1) [Service Manager 2](https://github.com/hmrc/sm2) installed
2) [SBT](https://www.scala-sbt.org) Version `>=1.x` installed
3) [MongoDB](https://www.mongodb.com/) version `>=3.6` installed and running on port 27017
4) [Localstack](https://github.com/localstack/localstack) installed and running on port 4572
5) Create an S3 bucket in localstack by using `awslocal s3 mb s3://digital-tariffs-local` within the localstack container

The easiest way to run MongoDB and Localstack for local development is to use [Docker](https://docs.docker.com/get-docker/).

##### To run Localstack and create the S3 bucket

```
> docker run -d --restart unless-stopped --name localstack -e SERVICES=s3 -p4572:4566 -p8080:8080 localstack/localstack
> docker exec -it localstack bash
> awslocal s3 mb s3://digital-tariffs-local
> exit
```

#### Starting the application:
 
Launch dependencies using `sm2 --start DIGITAL_TARIFFS_DEPS`.

Use `sbt run` to boot the app or run it with Service Manager 2 using `sm2 --start BINDING_TARIFF_FILESTORE`.

This application runs on port 9583.

You can also run the `DIGITAL_TARIFFS` profile using `sm2 --start DIGITAL_TARIFFS` and then stop the Service Manager 2 instance of this service using `sm2 --stop BINDING_TARIFF_FILESTORE` before running with sbt.

### Testing

Run `./run_all_tests.sh`. This also runs scalafmt and does coverage testing.

or `sbt test it/test` to run the tests only.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
