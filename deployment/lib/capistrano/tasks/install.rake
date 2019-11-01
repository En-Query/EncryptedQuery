desc "Install after build."
task :install do
  
  on roles(:all) do
    within "#{release_path}" do
      if "#{fetch :database_engine}" == "derbydb"
        execute "mkdir", "-p", "#{shared_path}/derbydb"
        execute "ln", "-s", "#{shared_path}/derbydb", "derbydb"
      end
    end
  end

  on roles(:responder) do |host|
    within "#{release_path}" do
      execute "cp", "responder/dist/target/encryptedquery-responder-dist-#{fetch(:database_engine)}-*.tar.gz", "responder.tar.gz"
      execute "cp", "flink/jdbc/target/encryptedquery-flink-jdbc-*.jar", "encryptedquery-flink-jdbc.jar"
      execute "cp", "flink/kafka/target/encryptedquery-flink-kafka-*.jar", "encryptedquery-flink-kafka.jar"
      execute "cp", "standalone/app/target/encryptedquery-standalone-app-*.jar", "encryptedquery-standalone-app.jar"
      execute "cp", "hadoop/mapreduce/target/encryptedquery-hadoop-mapreduce-*.jar", "encryptedquery-hadoop-mapreduce.jar"
      execute "rm", "-drf", "responder"
      execute "mkdir", "responder"
      execute "tar", "--strip-components=1", "-xf responder.tar.gz", "-C responder"

#      execute "cp", "-v", "#{shared_path}/responder-config/*.*", "responder/etc/", raise_on_non_zero_exit: false

      execute "mkdir", "inbox"
      execute "cp", "-v", "#{shared_path}/inbox/*.*", "inbox/" , raise_on_non_zero_exit: false

      execute "mkdir", "-p", "#{shared_path}/jobs/flink"
      execute "mkdir", "-p", "#{shared_path}/jobs/hadoop"
      execute "mkdir", "-p", "#{shared_path}/jobs/standalone"

      execute "ln", "-s", "#{shared_path}/jobs", "jobs"
      execute "ln", "-s", "#{shared_path}/results", "results"
      execute "ln", "-s", "#{shared_path}/sampledata", "sampledata"
      execute "ln", "-s", "#{shared_path}/responder-blob-storage", "responder-blob-storage"
    end
  end

  on roles(:querier) do |host|
    within "#{release_path}" do
      execute "cp", "querier/dist/target/encryptedquery-querier-dist-#{fetch(:database_engine)}-*.tar.gz", "querier.tar.gz"
      execute "rm", "-drf", "querier"

      execute "mkdir", "querier"
      execute "tar", "--strip-components=1", "-xf querier.tar.gz", "-C querier"

#      execute "cp", "-v", "#{shared_path}/querier-config/*.*", "querier/etc/" , raise_on_non_zero_exit: false

      execute "ln", "-s", "#{shared_path}/querier-blob-storage", "querier-blob-storage"
      execute "ln", "-s", "#{shared_path}/query-key-storage", "query-key-storage"
    end
  end
end