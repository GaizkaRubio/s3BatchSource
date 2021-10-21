/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package s3.source;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.api.batch.BatchSourceContext;
import s3.common.LineageRecorder;
import s3.format.PathTrackingInputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link BatchSource} that reads from Amazon S3.
 */
@Plugin(type = BatchSource.PLUGIN_TYPE)
@Name("S3")
@Description("Batch source to use Amazon S3 as a source.")
public class S3BatchSource extends AbstractFileSource<S3BatchSource.S3BatchConfig> {
  private static final String S3A_ACCESS_KEY = "fs.s3a.access.key";
  private static final String S3A_SECRET_KEY = "fs.s3a.secret.key";
  private static final String S3N_ACCESS_KEY = "fs.s3n.awsAccessKeyId";
  private static final String S3N_SECRET_KEY = "fs.s3n.awsSecretAccessKey";
  private static final String ACCESS_CREDENTIALS = "Access Credentials";

  private static final Logger LOG = LoggerFactory.getLogger(S3BatchSource.class);

  @SuppressWarnings("unused")
  private final S3BatchConfig config;

  public S3BatchSource(S3BatchConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  protected Map<String, String> getFileSystemProperties(BatchSourceContext context) {
    LOG.info("Get file properties");
    String authenticationMethod = config.authenticationMethod;
    Map<String, String> properties = new HashMap<>(config.getFilesystemProperties());
    if (authenticationMethod != null && authenticationMethod.equalsIgnoreCase(ACCESS_CREDENTIALS)) {
      if (config.path.startsWith("s3a://")) {
        properties.put(S3A_ACCESS_KEY, config.accessID);
        properties.put(S3A_SECRET_KEY, config.accessKey);
      } else if (config.path.startsWith("s3n://")) {
        properties.put(S3N_ACCESS_KEY, config.accessID);
        properties.put(S3N_SECRET_KEY, config.accessKey);
      }
    }
    if (config.shouldCopyHeader()) {
      properties.put(PathTrackingInputFormat.COPY_HEADER, "true");
    }
    if (config.getFileEncoding() != null && !config.getFileEncoding().equals(config.getDefaultFileEncoding())) {
      properties.put(PathTrackingInputFormat.SOURCE_FILE_ENCODING, config.getFileEncoding());
    }
    return properties;
  }

  @Override
  protected void recordLineage(LineageRecorder lineageRecorder, List<String> outputFields) {
    lineageRecorder.recordRead("Read", "Read from S3.", outputFields);
  }

  @Override
  protected boolean shouldGetSchema() {
    return !config.containsMacro(S3BatchConfig.NAME_PATH) && !config.containsMacro(S3BatchConfig.NAME_FORMAT) &&
      !config.containsMacro(S3BatchConfig.NAME_DELIMITER) && !config.containsMacro(S3BatchConfig.NAME_ACCESS_ID)
      && !config.containsMacro(S3BatchConfig.NAME_FILE_SYSTEM_PROPERTIES) &&
      !config.containsMacro(S3BatchConfig.NAME_ACCESS_KEY) && !config.containsMacro(S3BatchConfig.NAME_ACCESS_KEY);
  }

  /**
   * Config class that contains properties needed for the S3 source.
   */
  @SuppressWarnings("unused")
  public static class S3BatchConfig extends AbstractFileSourceConfig {
    private static final String NAME_ACCESS_ID = "accessID";
    private static final String NAME_ACCESS_KEY = "accessKey";
    private static final String NAME_PATH = "path";
    private static final String NAME_AUTH_METHOD = "authenticationMethod";
    private static final String NAME_FILE_SYSTEM_PROPERTIES = "fileSystemProperties";
    private static final String NAME_DELIMITER = "delimiter";

    private static final Gson GSON = new Gson();
    private static final Type MAP_STRING_STRING_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    @Macro
    @Description("Path to file(s) to be read. If a directory is specified, terminate the path name with a '/'. " +
      "The path must start with s3a:// or s3n://.")
    private String path;

    @Macro
    @Nullable
    @Description("Access ID of the Amazon S3 instance to connect to.")
    private String accessID;

    @Macro
    @Nullable
    @Description("Access Key of the Amazon S3 instance to connect to.")
    private String accessKey;

    @Macro
    @Nullable
    @Description("Authentication method to access S3. " +
      "Defaults to Access Credentials. URI scheme should be s3a:// for S3AFileSystem or s3n:// for " +
      "S3NativeFileSystem.")
    private String authenticationMethod;

    @Macro
    @Nullable
    @Description("Any additional properties to use when reading from the filesystem. " +
      "This is an advanced feature that requires knowledge of the properties supported by the underlying filesystem.")
    private String fileSystemProperties;

    public S3BatchConfig() {
      authenticationMethod = ACCESS_CREDENTIALS;
      fileSystemProperties = GSON.toJson(Collections.emptyMap());
    }

    @Override
    public void validate() {
      // no-op
    }

    @Override
    public void validate(FailureCollector collector) {
      super.validate(collector);
      if (ACCESS_CREDENTIALS.equals(authenticationMethod)) {
        if (!containsMacro("accessID") && (accessID == null || accessID.isEmpty())) {
          collector.addFailure("The Access ID must be specified if authentication method is Access Credentials.", null)
            .withConfigProperty(NAME_ACCESS_ID).withConfigProperty(NAME_AUTH_METHOD);
        }
        if (!containsMacro("accessKey") && (accessKey == null || accessKey.isEmpty())) {
          collector.addFailure("The Access Key must be specified if authentication method is Access Credentials.", null)
            .withConfigProperty(NAME_ACCESS_KEY).withConfigProperty(NAME_AUTH_METHOD);
        }
      }
      if (!containsMacro("path") && (!path.startsWith("s3a://") && !path.startsWith("s3n://"))) {
        collector.addFailure("Path must start with s3a:// or s3n://.", null).withConfigProperty(NAME_PATH);
      }
      if (!containsMacro(NAME_FILE_SYSTEM_PROPERTIES)) {
        try {
          getFilesystemProperties();
        } catch (Exception e) {
          collector.addFailure("File system properties must be a valid json.", null)
            .withConfigProperty(NAME_FILE_SYSTEM_PROPERTIES).withStacktrace(e.getStackTrace());
        }
      }
    }

    @Override
    public String getPath() {
      return path;
    }

    Map<String, String> getFilesystemProperties() {
      Map<String, String> properties = new HashMap<>();
      if (containsMacro("fileSystemProperties")) {
        return properties;
      }
      return GSON.fromJson(fileSystemProperties, MAP_STRING_STRING_TYPE);
    }
  }
}
