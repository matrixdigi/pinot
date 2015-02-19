package com.linkedin.pinot.core.segment.index.readers;

import java.io.File;
import java.io.IOException;

import com.linkedin.pinot.core.index.reader.impl.FixedByteWidthRowColDataFileReader;
import com.linkedin.pinot.core.indexsegment.utils.ByteBufferBinarySearchUtil;


/**
 * @author Dhaval Patel<dpatel@linkedin.com>
 * Nov 13, 2014
 */

public abstract class ImmutableDictionaryReader implements Dictionary {

  protected final FixedByteWidthRowColDataFileReader dataFileReader;
  private final ByteBufferBinarySearchUtil fileSearcher;
  private final int rows;

  public ImmutableDictionaryReader(File dictFile, int rows, int columnSize, boolean isMmap) throws IOException {
    if (isMmap) {
      dataFileReader = FixedByteWidthRowColDataFileReader.forMmap(dictFile, rows, 1, new int[] { columnSize });
    } else {
      dataFileReader = FixedByteWidthRowColDataFileReader.forHeap(dictFile, rows, 1, new int[] { columnSize });
    }
    this.rows = rows;
    fileSearcher = new ByteBufferBinarySearchUtil(dataFileReader);
  }

  public int getInt(int dictionaryId) {
    return dataFileReader.getInt(dictionaryId, 0);
  }

  public String getString(int dictionaryId) {
    return dataFileReader.getString(dictionaryId, 0);
  }

  public float getFloat(int dictionaryId) {
    return dataFileReader.getFloat(dictionaryId, 0);
  }

  public long getLong(int dictionaryId) {
    return dataFileReader.getLong(dictionaryId, 0);
  }

  public double getDouble(int dictionaryId) {
    return dataFileReader.getDouble(dictionaryId, 0);
  }

  public int intIndexOf(int actualValue) {
    return fileSearcher.binarySearch(0, actualValue);
  }

  public int floatIndexOf(float actualValue) {
    return fileSearcher.binarySearch(0, actualValue);
  }

  public int longIndexOf(long actualValue) {
    return fileSearcher.binarySearch(0, actualValue);
  }

  public int doubleIndexOf(double actualValue) {
    return fileSearcher.binarySearch(0, actualValue);
  }

  public int stringIndexOf(String actualValue) {
    return fileSearcher.binarySearch(0, actualValue);
  }

  public abstract int indexOf(Object rawValue);

  public abstract Object get(int dictionaryId);

  public abstract long getLongValue(int dictionaryId);

  public abstract double getDoubleValue(int dictionaryId);

  public abstract String toString(int dictionaryId);

  public void close() {
    dataFileReader.close();
  }

  public int length() {
    return rows;
  }
}