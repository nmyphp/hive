/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.llap.io.decode.orc.stream.readers;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.hive.common.DiskRange;
import org.apache.hadoop.hive.llap.io.api.EncodedColumnBatch;
import org.apache.hadoop.hive.llap.io.decode.orc.stream.SettableUncompressedStream;
import org.apache.hadoop.hive.llap.io.decode.orc.stream.StreamUtils;
import org.apache.hadoop.hive.ql.io.orc.CompressionCodec;
import org.apache.hadoop.hive.ql.io.orc.OrcProto;
import org.apache.hadoop.hive.ql.io.orc.PositionProvider;
import org.apache.hadoop.hive.ql.io.orc.RecordReaderImpl;

import com.google.common.collect.Lists;

/**
 * Stream reader for date column type.
 */
public class DateStreamReader extends RecordReaderImpl.DateTreeReader {
  private boolean isFileCompressed;
  private SettableUncompressedStream _presentStream;
  private SettableUncompressedStream _dataStream;

  private DateStreamReader(int columnId, SettableUncompressedStream present,
      SettableUncompressedStream data, boolean isFileCompressed,
      OrcProto.ColumnEncoding encoding) throws IOException {
    super(columnId, present, data, encoding);
    this.isFileCompressed = isFileCompressed;
    this._presentStream = present;
    this._dataStream = data;
  }

  @Override
  public void seek(PositionProvider index) throws IOException {
    if (present != null) {
      if (isFileCompressed) {
        index.getNext();
      }
      present.seek(index);
    }

    // data stream could be empty stream or already reached end of stream before present stream.
    // This can happen if all values in stream are nulls or last row group values are all null.
    if (_dataStream.available() > 0) {
      if (isFileCompressed) {
        index.getNext();
      }
      reader.seek(index);
    }
  }

  public void setBuffers(EncodedColumnBatch.StreamBuffer presentStreamBuffer,
      EncodedColumnBatch.StreamBuffer dataStreamBuffer) {
    long length;
    if (_presentStream != null) {
      List<DiskRange> presentDiskRanges = Lists.newArrayList();
      length = StreamUtils.createDiskRanges(presentStreamBuffer, presentDiskRanges);
      _presentStream.setBuffers(presentDiskRanges, length);
    }
    if (_dataStream != null) {
      List<DiskRange> dataDiskRanges = Lists.newArrayList();
      length = StreamUtils.createDiskRanges(dataStreamBuffer, dataDiskRanges);
      _dataStream.setBuffers(dataDiskRanges, length);
    }
  }

  public static class StreamReaderBuilder {
    private Long fileId;
    private int columnIndex;
    private EncodedColumnBatch.StreamBuffer presentStream;
    private EncodedColumnBatch.StreamBuffer dataStream;
    private CompressionCodec compressionCodec;
    private OrcProto.ColumnEncoding columnEncoding;

    public StreamReaderBuilder setFileId(Long fileId) {
      this.fileId = fileId;
      return this;
    }

    public StreamReaderBuilder setColumnIndex(int columnIndex) {
      this.columnIndex = columnIndex;
      return this;
    }

    public StreamReaderBuilder setPresentStream(EncodedColumnBatch.StreamBuffer presentStream) {
      this.presentStream = presentStream;
      return this;
    }

    public StreamReaderBuilder setDataStream(EncodedColumnBatch.StreamBuffer dataStream) {
      this.dataStream = dataStream;
      return this;
    }

    public StreamReaderBuilder setCompressionCodec(CompressionCodec compressionCodec) {
      this.compressionCodec = compressionCodec;
      return this;
    }

    public StreamReaderBuilder setColumnEncoding(OrcProto.ColumnEncoding encoding) {
      this.columnEncoding = encoding;
      return this;
    }

    public DateStreamReader build() throws IOException {
      SettableUncompressedStream present = StreamUtils.createLlapInStream(OrcProto.Stream.Kind.PRESENT.name(),
          fileId, presentStream);


      SettableUncompressedStream data = StreamUtils.createLlapInStream(OrcProto.Stream.Kind.DATA.name(), fileId, 
          dataStream);

      boolean isFileCompressed = compressionCodec != null;
      return new DateStreamReader(columnIndex, present, data, isFileCompressed,
          columnEncoding);
    }
  }

  public static StreamReaderBuilder builder() {
    return new StreamReaderBuilder();
  }
}
