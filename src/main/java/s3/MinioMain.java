package s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;

public class MinioMain {

  public static void main(String... args) {
    AWSCredentials credentials = new BasicAWSCredentials("dynoadmin", "Dyn@musr");
    ClientConfiguration clientCfg = new ClientConfiguration();
    S3ClientOptions options = new S3ClientOptions();
    options.setPathStyleAccess(true);
    clientCfg.setProtocol(Protocol.HTTP);
    clientCfg.setProxyHost("de1app02903n-2.internal.vodafone.com");
    clientCfg.setProxyPort(8080);
    /*AmazonS3 s3Client = AmazonS3Client.builder()
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
        .withClientConfiguration(clientCfg)
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("https://minio.bit-rat-tst-1.rat.gks.vodafone.com", Regions.US_EAST_1.name()))
        .withPathStyleAccessEnabled(true)
        .build();*/
    AmazonS3 s3Client = new AmazonS3Client(credentials, clientCfg);
    s3Client.setS3ClientOptions(options);
    s3Client.setEndpoint("https://minio.bit-rat-tst-1.rat.gks.vodafone.com");
    s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));
    System.out.println("Metadata New library: ");
    s3Client.getObjectMetadata("dynamo", "cities_1.csv")
        .getRawMetadata().forEach((key, value) ->System.out.println(key + ": " + value));
    System.out.println("Content-Length" + s3Client.getObjectMetadata("dynamo", "cities_1.csv").getRawMetadata().containsKey("Content-Length"));
    System.out.println("content-length" + s3Client.getObjectMetadata("dynamo", "cities_1.csv").getRawMetadata().containsKey("content-length"));
    Long lwf = s3Client.getObjectMetadata("dynamo", "cities_1.csv").getContentLength();
    System.out.println("Len " + lwf);
  }
}
