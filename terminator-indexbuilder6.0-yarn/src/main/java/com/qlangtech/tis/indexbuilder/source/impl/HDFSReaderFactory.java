/* 
 * The MIT License
 *
 * Copyright (c) 2018-2022, qinglangtech Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.qlangtech.tis.indexbuilder.source.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.solr.schema.IndexSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.qlangtech.tis.hdfs.TISHdfsUtils;
import com.qlangtech.tis.indexbuilder.map.Counters;
import com.qlangtech.tis.indexbuilder.map.IndexConf;
import com.qlangtech.tis.indexbuilder.map.Messages;
import com.qlangtech.tis.indexbuilder.map.TaskContext;
import com.qlangtech.tis.indexbuilder.source.SourceReader;
import com.qlangtech.tis.indexbuilder.source.SourceReaderFactory;
import com.qlangtech.tis.indexbuilder.utils.Context;
import com.qlangtech.tis.manage.common.IndexBuildParam;

/* *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2019年1月17日
 */
public class HDFSReaderFactory implements SourceReaderFactory {

    public static final Logger logger = LoggerFactory.getLogger(HDFSReaderFactory.class);

    protected Context context;

    protected IndexConf indexConf;

    protected final FileSystem fs;

    protected List<FileSplit> fileSplits;

    public HDFSReaderFactory() {
        super();
        this.fs = TISHdfsUtils.getFileSystem();
    }

    private IndexSchema indexSchema;

    // private Integer allRowCount;
    // protected BlockingQueue<FileSplit> splitQueues;
    // private String delimiter = "\t";
    // /**
    // * 取得所有条数统计总和
    // *
    // * @return
    // */
    // public Integer getAllRowCount() {
    // return this.allRowCount;
    // }
    public IndexSchema getIndexSchema() {
        return indexSchema;
    }

    public void setIndexSchema(IndexSchema indexSchema) {
        this.indexSchema = indexSchema;
    }

    protected TaskContext taskContext;

    private String[] titleText;

    private final AtomicInteger fileSplitsindex = new AtomicInteger();

    public SourceReader nextReader() throws Exception {
        if (readOver()) {
            return null;
        }
        FileSplit split = null;
        int currentIndex = fileSplitsindex.getAndIncrement();
        if (currentIndex >= fileSplits.size()) {
            return null;
        }
        split = fileSplits.get(currentIndex);
        // FileSplit split = (FileSplit) this.splitQueues.poll();
        // if (split != null) {
        HDFSReader reader = new HDFSReader(this.context, split);
        reader.setTitleText(this.titleText);
        if (indexSchema == null || indexSchema.getUniqueKeyField() == null) {
            throw new IllegalStateException("either indexSchema or schema.uniquekey  is null");
        }
        reader.setUniqueKey(indexSchema.getUniqueKeyField().getName());
        // }
        return reader;
    // }
    // return null;
    }

    protected boolean readOver() {
        return fileSplitsindex.get() >= fileSplits.size();
    }

    public void init(Context context) throws Exception {
        this.context = context;
        this.taskContext = ((TaskContext) context.get("taskcontext"));
        this.indexConf = ((IndexConf) context.get("indexconf"));
        String buildtabletitleitems = taskContext.getUserParam(IndexBuildParam.INDEXING_BUILD_TABLE_TITLE_ITEMS);
        if (StringUtils.isBlank(buildtabletitleitems)) {
            throw new IllegalStateException(" indexing.buildtabletitleitems shall be set in user param ");
        }
        // readerContext.put("titletext",
        // );
        this.titleText = StringUtils.split(buildtabletitleitems, ",");
        Counters counters = this.taskContext.getCounters();
        Messages messages = this.taskContext.getMessages();
        context.put("filesystem", this.fs);
        // String fileSplitorClass = this.indexConf.getFileSplitor();
        FileSplitor fileSplitor = null;
        // if (fileSplitorClass == null) {
        fileSplitor = new DefaultFileSplitor(this.indexConf, this.fs);
        // } else {
        // fileSplitor = (FileSplitor)
        // Class.forName(fileSplitorClass).newInstance();
        // fileSplitor.setFileSystem(this.fs);
        // fileSplitor.setIndexConf(this.indexConf);
        // }
        this.fileSplits = fileSplitor.getSplits();
        if (this.fileSplits.size() == 0) {
            throw new Exception("源数据为空！");
        }
    // long current = System.currentTimeMillis();
    // int rowCount = 0;
    // // 读总条数
    // SourceReader reader = null;
    // while ((reader = nextReader()) != null) {
    // try {
    // while (reader.next() != null) {
    // rowCount++;
    // }
    // } finally {
    // IOUtils.closeQuietly(reader);
    // }
    // }
    // this.allRowCount = rowCount;
    // this.fileSplitsindex.getAndSet(0);
    // logger.info("statis row count consume:" + (System.currentTimeMillis()
    // - current)
    // + "ms,rows:" + rowCount);
    }

    private int getRecordCount() throws IOException {
        FileStatus[] arrayOfFileStatus;
        if ((arrayOfFileStatus = this.fs.listStatus(new Path(this.indexConf.getSourcePath()), new PathFilter() {

            public boolean accept(Path path) {
                String name = path.getName();
                return name.endsWith(".suc");
            }
        })).length != 0) {
            FileStatus stat = arrayOfFileStatus[0];
            BufferedInputStream in = null;
            FSDataInputStream fileIn = null;
            try {
                fileIn = this.fs.open(stat.getPath());
                stat.getLen();
            } catch (IOException e) {
                e.printStackTrace();
            }
            in = new BufferedInputStream(fileIn);
            return readInt(in, '\n');
        }
        return 0;
    }

    private int readInt(InputStream in, char delimiter) throws IOException {
        int i = 0;
        byte[] bytes = new byte[10];
        while (true) {
            int b = in.read();
            if (b == -1) {
                break;
            }
            byte c = (byte) b;
            if ((c == 10) || (c == delimiter)) {
                break;
            }
            bytes[(i++)] = c;
        }
        byte[] t = new byte[i];
        for (int j = 0; j < i; j++) {
            t[j] = bytes[j];
        }
        return Integer.valueOf(new String(t)).intValue();
    }
    // public SuccessFlag getSuccessFlag() throws Exception {
    // SuccessFlag flag = new SuccessFlag();
    // flag.setFlag(SuccessFlag.Flag.SUCCESS);
    // return flag;
    // }
}
