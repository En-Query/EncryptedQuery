desc 'Upload config file to shared/config/*.* files with the proper ones for role'
task :upload_config do

#  puts "upload_config called"

  responder_directories = [
    "#{fetch(:stage_config_path)}/#{fetch(:stage)}/responder",
    "#{fetch(:stage_config_path)}/#{fetch(:stage)}"
  ]


  #  invoke :collect_local_file_checksums, responder_directories, responder_files

  querier_directories = [
    "#{fetch(:stage_config_path)}/#{fetch(:stage)}/querier",
    "#{fetch(:stage_config_path)}/#{fetch(:stage)}"
  ]

  #invoke :collect_local_file_checksums, querier_directories, querier_files
  
  def get_files(dirs)
    result = []
    dirs.each do |directory|
      next if !File::exists?(directory)
      Dir.chdir(directory) do
        Dir.glob("*.*") do |file_name|
          next if result.include? file_name
          result << File.join(Dir.pwd, file_name) 
        end
      end
    end
    return result
  end
  
  responder_files = []
  querier_files = []
  
  run_locally do
    responder_files = get_files(responder_directories)
    querier_files = get_files(querier_directories)
  end

#  puts "responder_files=" + responder_files.inspect
#  puts "querier_files=" + querier_files.inspect

  on roles(:querier) do
    within "#{release_path}/querier/etc" do
      querier_files.each do |file_name|
        expanded = ERB.new(File.read(file_name)).result(binding)
        upload! StringIO.new(expanded), File.basename(file_name)
      end
    end
  end

  on roles(:responder) do
    within "#{release_path}/responder/etc" do
      responder_files.each do |file_name|
         expanded = ERB.new(File.read(file_name)).result(binding)
         upload! StringIO.new(expanded), File.basename(file_name)
      end
    end
  end
end
