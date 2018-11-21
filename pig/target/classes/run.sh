DIR=hdfs:///user/pirk/
#RUN_MODE="-x local"
#FNAME=cdr100M
#FNAME=cdr5k
FNAME=cdr1M
PIG_OPTS="-Dmapreduce.reduce.memory.mb=3072"
PIG_OPTS="$PIG_OPTS -Dmapreduce.reduce.java.opts=-Xmx2764m"
PIG_OPTS="$PIG_OPTS -Dmapreduce.map.memory.mb=2000"
PIG_OPTS="$PIG_OPTS -Dmapreduce.map.java.opts=-Xmx2000m"
PIG_OPTS="$PIG_OPTS -Dmapreduce.task.io.sort.mb=756"
PIG_OPTS="$PIG_OPTS -Dyarn.app.mapreduce.am.resource.mb=3584"
PIG_OPTS="$PIG_OPTS -Dyarn.app.mapreduce.am.command-opts=-Xmx3096m"
PIG_OPTS="$PIG_OPTS -Ddefault_parallel=8"

export PIG_OPTS
PIG_HEAPSIZE=2500 \
/opt/pig/bin/pig -param input_file=${DIR}${FNAME}.json \
                 -param output_file=${DIR}${FNAME}-response.bin \
                 -param query_file_name=standalone-demo-query    \
                 -param data_schema_dir=data-schemas          \
                 $RUN_MODE test.pig