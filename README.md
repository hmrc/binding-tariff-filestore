
# binding-tariff-filestore

The backend filestore service which manages attachment metadata and S3 bucket access for the Advance Tariff Rulings services.

### Running

##### To run this Service you will need:

1. **Service Manager 2** installed
2. **SBT Version >=1.x** installed
3. **MongoDB version >=3.6** installed and running on **port: 27017**
4. **Moto (MotoServer)** running on **port: 4566**

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

### Moto (S3)

[Moto](https://github.com/getmoto/moto) (MotoServer) replaces LocalStack. It listens on port 5000 inside the container, mapped to **4566** locally.

Start Moto:

```bash
docker run -d \
  --restart unless-stopped \
  --name moto \
  -p 4566:5000 \
  motoserver/moto:5.1.18
```
---
### Verify Moto is running

```bash
docker ps
```

You should see:

```
moto   Up ...   0.0.0.0:4566->5000/tcp
```
---

## Configure AWS credentials (for Moto)

Moto accepts any credentials, but they must exist.

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

Run from your machine:

```bash
aws --endpoint-url=http://localhost:4566 \
  s3 mb s3://digital-tariffs-local
```

Verify:

```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

---
### Reset Moto completely

```bash
docker rm -f moto
```

Then start it again and recreate the bucket.

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
