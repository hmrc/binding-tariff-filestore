
# binding-tariff-filestore

The backend filestore service which manages attachment metadata and S3 bucket access for the Advance Tariff Rulings services.

### Running

##### To run this Service you will need:

1. **Service Manager 2** installed
2. **SBT Version >=1.x** installed
3. **MongoDB version >=3.6** installed and running on **port: 27017**
4. **Localstack** installed and running on **port: 4566**

---

## Local Dependencies (Docker)

### MongoDB

```bash
docker run --restart unless-stopped -d \
  -p 27017:27017 \
  --name mongodb \
  mongo:5.0
```

---

### Localstack (S3)

LocalStack uses a single edge port: **4566**

Start LocalStack:

```bash

docker run -d \
  --restart unless-stopped \
  --name localstack \
  -e SERVICES=s3 \
  -e DEFAULT_REGION=eu-west-2 \
  -p 4566:4566 \
  localstack/localstack:4.4.0
```
---
### Verify LocalStack is running

```bash
docker ps
```

You should see:

```
localstack   Up ...   0.0.0.0:4566->4566/tcp
```
---

## Configure AWS credentials (for LocalStack)

LocalStack accepts any credentials, but they must exist.

Run:

```bash
aws configure
```

Use:

```
AWS Access Key ID: test
AWS Secret Access Key: test
Default region name: eu-west-2
Default output format:
```

---
## Create S3 bucket

Run from your machine (not inside the container):

```bash
aws --endpoint-url=http://localhost:4566 \
  s3 mb s3://digital-tariffs-local
```

Verify:

```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

---
### Reset LocalStack completely

```bash
docker rm -f localstack
docker volume prune -f
```

Then start it again.

---


#### Starting the application:

Launch services using `sm2 --start DIGITAL_TARIFFS`

If you want to run it locally:

- `sm2 --stop BINDING_TARIFF_FILESTORE`
- `sbt run`

This application runs on port 9583.

### Testing

Run `./run_all_tests.sh`. This also runs scalafmt and does coverage testing.

or `sbt test it/test` to run the tests only.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
