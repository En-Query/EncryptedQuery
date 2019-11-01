desc 'Collect checksums of local files'
task :collect_local_file_checksums, :dirs, :files do |t, args|
  run_locally do
    args[:dirs].each do |directory|
      next if !File::exists?(directory)
      Dir.chdir(directory) do
        Dir.glob("*.*") do |file_name|
          next if args[:files].keys.include? file_name
          checksum = capture "cksum", File.join(Dir.pwd, file_name)
          args[:files][file_name] = checksum
        end
      end
    end
    t.reenable
  end
end