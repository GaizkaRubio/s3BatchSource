/*
 * Copyright © 2018-2019 Cask Data, Inc.
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

package s3.format;

import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s3.source.AbstractFileSource;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Text format that tracks which file each record was read from.
 */
public class PathTrackingTextInputFormat extends PathTrackingInputFormat {

  private static final Logger LOG = LoggerFactory.getLogger(PathTrackingTextInputFormat.class);
  private final boolean emittedHeader;

  /**
   * @param emittedHeader whether the header was already emitted. This is the case when there are multiple files in
   *   the same split. The delegate RecordReader for the first split will emit it, and we will need the delegate
   *   RecordReaders for the other files to skip it.
   */
  public PathTrackingTextInputFormat(boolean emittedHeader) {
    this.emittedHeader = emittedHeader;
  }

  @Override
  protected RecordReader<NullWritable, StructuredRecord.Builder> createRecordReader(FileSplit split,
                                                                                    TaskAttemptContext context,
                                                                                    @Nullable String pathField,
                                                                                    Schema schema) {
    RecordReader<LongWritable, Text> delegate = getDefaultRecordReaderDelegate(split, context);
    String header = context.getConfiguration().get(CombineTextInputFormat.HEADER);
    boolean skipHeader = context.getConfiguration().getBoolean(CombineTextInputFormat.SKIP_HEADER, false);
    return new TextRecordReader(delegate, schema, emittedHeader, header, skipHeader);
  }

  /**
   * Text record reader
   */
  static class TextRecordReader extends RecordReader<NullWritable, StructuredRecord.Builder> {
    private final RecordReader<LongWritable, Text> delegate;
    private final Schema schema;
    private final String header;
    private final boolean setOffset;
    private final boolean skipHeader;
    private boolean emittedHeader;

    TextRecordReader(RecordReader<LongWritable, Text> delegate, Schema schema, boolean emittedHeader,
                     @Nullable String header, boolean skipHeader) {
      this.delegate = delegate;
      this.schema = schema;
      this.emittedHeader = emittedHeader;
      this.header = header;
      this.setOffset = schema.getField("offset") != null;
      this.skipHeader = skipHeader;
    }

    @Override
    public void initialize(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
      delegate.initialize(split, context);
    }

    @Override
    public boolean nextKeyValue() throws IOException, InterruptedException {
      if (header != null && !emittedHeader) {
        return true;
      }

      if (delegate.nextKeyValue()) {
        // if this record is the actual header and we've already emitted the copied header or we want to skip header
        if ((skipHeader || (header != null && emittedHeader)) && delegate.getCurrentKey().get() == 0L) {
          return delegate.nextKeyValue();
        }
        return true;
      }
      return false;
    }

    @Override
    public NullWritable getCurrentKey() {
      return NullWritable.get();
    }

    @Override
    public StructuredRecord.Builder getCurrentValue() throws IOException, InterruptedException {
      StructuredRecord.Builder recordBuilder = StructuredRecord.builder(schema);
      if (header != null && !emittedHeader) {
        emittedHeader = true;
        if (setOffset) {
          recordBuilder.set("offset", 0L);
        }
        recordBuilder.set("body", header);
      } else {
        if (setOffset) {
          recordBuilder.set("offset", delegate.getCurrentKey().get());
        }
        recordBuilder.set("body", delegate.getCurrentValue().toString());
      }
      return recordBuilder;
    }

    @Override
    public float getProgress() throws IOException, InterruptedException {
      return delegate.getProgress();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
