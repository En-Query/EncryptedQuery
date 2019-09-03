package org.enquery.encryptedquery.hadoop.core;

import java.io.IOException;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;


/**
 * Mapper class for the CombineColumnResults job
 *
 * <p>
 * This is a pass-through mapper that reads in pairs {@code
 * (startCol, encvalue)} and emits the same key-value pair unchanged.
 */
public class CombineColumnResultsMapper extends Mapper<LongWritable, BytesWritable, LongWritable, BytesWritable> {

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);
	}

	@Override
	public void map(LongWritable colIndex, BytesWritable encryptedColumns, Context ctx) throws IOException, InterruptedException {
		ctx.write(colIndex, encryptedColumns);
	}
}
