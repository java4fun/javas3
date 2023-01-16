package org.frankmoley.lil.s3;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    private static final String AWS_ACCESS_KEY = "AWS_ACCESS_KEY_ID";
    private static final String AWS_SECRET_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String PRI_BUCKET_NAME = "fpmlil";
    private static final String TRANSIENT_BUCKET_NAME = "fpmlil2";
    private static final String F1 = "lil1.txt";
    private static final String F2 = "lil2.txt";
    private static final String F3 = "lil3.txt";
    private static final String DIR = "/Users/fpmoles/Desktop/s3";
    private static final String DOWN_DIR = "/Users/fpmoles/Desktop/s3alt";

    private final S3Client s3;
    private final S3Presigner presigner;

    public Application(S3Client s3, S3Presigner presigner){
        this.s3 = s3;
        this.presigner = presigner;
    }

    public void createBucket(String name){
        try{
            CreateBucketRequest request = CreateBucketRequest
                    .builder()
                    .bucket(name)
                    .build();
            s3.createBucket(request);
        }catch(Exception e){
            LOGGER.error("error during create bucket", e);
        }
    }

    public void uploadFile(String bucket, String localFile, String localDirectory, String key){
        try{
            PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).build();
            s3.putObject(request, Paths.get(localDirectory, localFile));
        }catch (Exception e){
            LOGGER.error("error uploading file", e);
        }
    }

    public void downloadFile(String bucket, String localFile, String localDirectory, String key){
        try{
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
            s3.getObject(request, Paths.get(localDirectory, localFile));
        }catch (Exception e){
            LOGGER.error("error downloading file", e);
        }
    }

    public void deleteFile(String bucket, String key){
        try{
            DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
            s3.deleteObject(request);
        }catch (Exception e){
            LOGGER.error("error deleting file", e);
        }
    }

    public List<String> listFiles(String bucket){
        List<String> keys = new ArrayList();
        try{
            ListObjectsRequest request = ListObjectsRequest.builder().bucket(bucket).build();
            ListObjectsResponse response = s3.listObjects(request);
            response.contents().forEach(content->{
                keys.add(content.key());
            });
            LOGGER.info("Number of files in bucket: " + keys.size());
        }catch (Exception e){
            LOGGER.error("error listing files", e);
        }
        return keys;
    }

    public void copyFile(String sourceBucket, String destinationBucket, String sourceKey, String destinationKey){
        try{
            String encodedUrl = URLEncoder.encode(sourceBucket + "/" + sourceKey, StandardCharsets.UTF_8.toString());
            CopyObjectRequest request = CopyObjectRequest.builder().copySource(encodedUrl)
                    .destinationBucket(destinationBucket).destinationKey(destinationKey).build();
            s3.copyObject(request);
        }catch (Exception e){
            LOGGER.error("error copying file", e);
        }
    }

    public void blockPublicAccess(String bucket){
        try{
            PutPublicAccessBlockRequest request = PutPublicAccessBlockRequest
                    .builder()
                    .bucket(bucket)
                    .publicAccessBlockConfiguration(PublicAccessBlockConfiguration
                            .builder()
                            .blockPublicAcls(true)
                            .blockPublicPolicy(true)
                            .ignorePublicAcls(true)
                            .restrictPublicBuckets(true)
                            .build())
                    .build();
            s3.putPublicAccessBlock(request);
        }catch (Exception e){
            LOGGER.error("error blocking public access", e);
        }
    }

    public String createPresignedUrl(String bucket, String key){
        String result = null;
        try{
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build())
                    .signatureDuration(Duration.ofSeconds(30))
                    .build();
            PresignedGetObjectRequest pRequest = presigner.presignGetObject(request);
            result = pRequest.url().toString();
        }catch (Exception e){
            LOGGER.error("Error generating presigned url", e);
        }
        return result;
    }

    public void deleteBucket(String bucket){
        try{
            DeleteBucketRequest request = DeleteBucketRequest.builder()
                    .bucket(bucket)
                    .build();
            s3.deleteBucket(request);
        }catch (Exception e){
            LOGGER.error("Error deleting bucket", e);
        }
    }

    public static void main(String[] args) {
        String accessKey = System.getenv(AWS_ACCESS_KEY);
        String secretKey = System.getenv(AWS_SECRET_KEY);
        AwsSessionCredentials creds = AwsSessionCredentials.create(accessKey, secretKey, "");

        S3Client s3 = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(creds)).build();
        S3Presigner presigner = S3Presigner.builder().credentialsProvider(StaticCredentialsProvider.create(creds)).build();
        Application app = new Application(s3, presigner);

        app.createBucket(TRANSIENT_BUCKET_NAME);
        app.uploadFile(TRANSIENT_BUCKET_NAME, F2, DIR, F2);
        app.copyFile(PRI_BUCKET_NAME, TRANSIENT_BUCKET_NAME, F1, F1);
        List<String> keys = app.listFiles(TRANSIENT_BUCKET_NAME);
        System.out.println("These are the files in the transient bucket");
        keys.forEach(System.out::println);
        app.downloadFile(PRI_BUCKET_NAME, F1, DOWN_DIR, F1);
        String url = app.createPresignedUrl(TRANSIENT_BUCKET_NAME, F1);
        keys.forEach(key->{
            app.deleteFile(TRANSIENT_BUCKET_NAME, key);
        });
        app.deleteBucket(TRANSIENT_BUCKET_NAME);

    }
}
