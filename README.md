# Vert.x S3 client

A fully functional Vert.x client for S3

## Compatibility
- Java 8+
- Vert.x 3.x.x

## Dependencies

### Dependency Vert.x 3.x.x
### Maven
```xml
<dependency>
    <groupId>com.hubrick.vertx</groupId>
    <artifactId>vertx-s3-client</artifactId>
    <version>3.3.3</version>
</dependency>
```

## How to use

### Common operations
```java
        final S3ClientOptions clientOptions = new S3ClientOptions()
                .setAwsRegion("eu-central-1")
                .setAwsServiceName("s3")
                .setAwsAccessKey("AKIDEXAMPLE")
                .setAwsSecretKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
                //.setSignPayload(true);

        final S3Client s3Client = new S3Client(vertx, clientOptions);

        s3Client.getObject(
                "bucket", 
                "key",
                new GetObjectRequest().withResponseContentType("application/json"),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );
        
        s3Client.getObjectAcl(
                "bucket", 
                "key",
                response -> System.out.println("Response from AWS: " + response.getData().getOwner()),
                Throwable::printStackTrace
        );

        s3Client.putObject(
                "bucket", 
                "key",
                new PutObjectRequest(Buffer.buffer("test")).withContentType("application/json"),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );
        
        s3Client.putObjectAcl(
                "bucket", 
                "key",
                new PutObjectAclRequest(new AclHeadersRequest().withAmzAcl(CannedAcl.PRIVATE)),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );
        
        s3Client.deleteObject(
                "bucket", 
                "key",
                new DeleteObjectRequest(),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );
        
        s3Client.copyObject(
                "sourceBucket", 
                "sourceKey",
                "destinationBucket", 
                "destinationKey",
                new CopyObjectRequest(),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );
        
        s3Client.headObject(
                "bucket", 
                "key",
                new HeadObjectRequest(),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );
        
        // List bucket v2
        s3Client.getBucket(
                "bucket",
                new GetBucketRequest().withPrefix("prefix"),
                response -> System.out.println("Response from AWS: " + response.getData().getName()),
                Throwable::printStackTrace
        );
        
        // putObject with server-side-encryption
        s3Client.putObjectSse(
                S3_BUCKET_NAME,
                KEY_ENCRYPTED,
                new PutObjectRequest(Buffer.buffer(TEST_FILE_CONTENT))
                        .withContentType(TEXT_PLAIN),
                new SseHeadersRequest<>()
                        .withAmzServerSideEncryptionCustomerAlgorithm(AES_256)
                        .withAmzServerSideEncryptionCustomerKey(SSE_KEY)
                        .withAmzServerSideEncryptionCustomerKeyMD5(SSE_KEY_MD5),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );

        // getObject with server-side-encryption
        s3Client.getObjectSse(
                S3_BUCKET_NAME,
                KEY_ENCRYPTED,
                new GetObjectRequest()
                        .withResponseContentType(TEXT_PLAIN),
                new SseHeadersRequest<>()
                        .withAmzServerSideEncryptionCustomerAlgorithm(AES_256)
                        .withAmzServerSideEncryptionCustomerKey(SSE_KEY)
                        .withAmzServerSideEncryptionCustomerKeyMD5(SSE_KEY_MD5),
                response -> System.out.println("Response from AWS: " + response.getHeader().getContentType()),
                Throwable::printStackTrace
        );
```

### Multipart upload
```java
        final S3ClientOptions clientOptions = new S3ClientOptions()
                .setAwsRegion("eu-central-1")
                .setAwsServiceName("s3")
                .setAwsAccessKey("AKIDEXAMPLE")
                .setAwsSecretKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
                //.setSignPayload(true);

        final S3Client s3Client = new S3Client(vertx, clientOptions);

        // Stream any file from disk to S3
        vertx.fileSystem().open(filePath, openOptions, asyncFile -> {
        
            asyncFile.pause();
            s3Client.initMultipartUpload(
                "bucket",
                "someid",
                new InitMultipartUploadRequest(asyncFile).withContentType("video/mp4"),
                response -> {
                    asyncFile.endHandler(aVoid -> response.getData().end());
                    asyncFile.exceptionHandler(Throwable::printStackTrace);
                    final Pump pump = Pump.pump(asyncFile, response.getData());
                    pump.start();
                    asyncFile.resume();
                },
                Throwable::printStackTrace
            );
        });
```

### Adaptive upload
S3 will return a error code in case multipart upload is used and the parts are smaller then 5MB. To not deal with the detection of the stream size adaptive upload can be used. The client will automaticaly detect the size of the stream. If it's lower then 5MB it will directly upload the file to S3, otherwise it will stream the data using the mutlipart upload.
```java
        final S3ClientOptions clientOptions = new S3ClientOptions()
                .setAwsRegion("eu-central-1")
                .setAwsServiceName("s3")
                .setAwsAccessKey("AKIDEXAMPLE")
                .setAwsSecretKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
                //.setSignPayload(true);

        final S3Client s3Client = new S3Client(vertx, clientOptions);

        // Stream any file from disk to S3
        vertx.fileSystem().open(filePath, openOptions, asyncFile -> {
        
            s3Client.adaptiveUpload(
                "bucket",
                "someid",
                new AdaptiveUploadRequest(asyncFile).withContentType("video/mp4"),
                response -> {
                    // Response headers
                },
                Throwable::printStackTrace
            );
        });
```

## Error handling
In case some error happens on S3 side a HttpErrorException is thrown which contains the unmarshalled ErrorResponse object from S3. 
 
## License
Apache License, Version 2.0

Contains the "AWS Signature Version 4 Test Suite" 
from http://docs.aws.amazon.com/general/latest/gr/signature-v4-test-suite.html
Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under Apache License, Version 2.0


